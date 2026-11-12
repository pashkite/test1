//! Shared screen-comparison and OCR-label classification core (Rust).
//! No network, game process memory reads, injection, or game protocol calls.

pub fn similarity_argb(left: &[u32], right: &[u32]) -> f32 {
    if left.len() != right.len() || left.is_empty() { return 0.0; }
    let mut error: u64 = 0;
    let mut count: u64 = 0;
    let step = (left.len() / 2400).max(1);
    for idx in (0..left.len()).step_by(step) {
        let a = left[idx];
        let b = right[idx];
        for shift in [0u32, 8, 16] {
            let a = ((a >> shift) & 0xff) as i32;
            let b = ((b >> shift) & 0xff) as i32;
            error += (a - b).unsigned_abs() as u64;
        }
        count += 1;
    }
    (1.0 - (error as f32 / (count * 3 * 255) as f32)).clamp(0.0, 1.0)
}

/// Return 1 for win-rate selection, 2 for begin battle, 3 for victory/clear.
pub fn classify_label(text: &str) -> i32 {
    let normalized: String = text.chars()
        .filter(|c| !c.is_whitespace() && *c != ':' && *c != '：' && *c != '!' && *c != '·')
        .flat_map(|c| c.to_uppercase())
        .collect();
    if normalized.is_empty() || normalized.chars().count() > 28 { return 0; }
    if normalized.contains("승률") || normalized == "WINRATE" { return 1; }
    if ["전투시작", "전투개시", "START", "BATTLESTART", "FIGHT", "전투진행"].contains(&normalized.as_str()) { return 2; }
    if ["VICTORY", "STAGECLEAR", "BATTLECLEAR", "전투결과", "전투승리", "클리어", "CLEAR"].contains(&normalized.as_str()) { return 3; }
    0
}

#[cfg(target_os = "android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_ashkite_limbusauto_NativeVision_classify(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    input: jni::objects::JString,
) -> jni::sys::jint {
    match env.get_string(&input) {
        Ok(s) => classify_label(&String::from(s)),
        Err(_) => 0
    }
}

#[cfg(target_os = "android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_ashkite_limbusauto_NativeVision_similarity(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    left: jni::objects::JIntArray,
    right: jni::objects::JIntArray,
) -> jni::sys::jfloat {
    let (Ok(la), Ok(lb))=(env.get_array_length(&left),env.get_array_length(&right)) else {return 0.0};
    if la!=lb || la<=0 || la>200_000 {return 0.0}
    let mut a=vec![0i32;la as usize];let mut b=vec![0i32;lb as usize];
    if env.get_int_array_region(&left,0,&mut a).is_err() || env.get_int_array_region(&right,0,&mut b).is_err(){return 0.0}
    similarity_argb(&a.into_iter().map(|x|x as u32).collect::<Vec<_>>(),
                    &b.into_iter().map(|x|x as u32).collect::<Vec<_>>())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn identical(){assert_eq!(similarity_argb(&[0xffaa5511;100],&[0xffaa5511;100]),1.0);}
    #[test] fn different(){assert!(similarity_argb(&[0xff000000;100],&[0xffffffff;100])<0.01);}
    #[test] fn unequal(){assert_eq!(similarity_argb(&[1,2],&[1]),0.0);}
    #[test] fn empty(){assert_eq!(similarity_argb(&[],&[]),0.0);}
    #[test] fn korean(){assert_eq!(classify_label("승 률"),1);}
    #[test] fn latin(){assert_eq!(classify_label("Win Rate"),1);}
    #[test] fn start(){assert_eq!(classify_label("전투 시작"),2);assert_eq!(classify_label("START"),2);}
    #[test] fn clear(){assert_eq!(classify_label("STAGE CLEAR"),3);}
    #[test] fn avoid_text(){assert_eq!(classify_label("게임 시작하기 전에 확인"),0);}
}


// Low-resolution template matching: exact icon shapes, not OCR text coordinates.
// Image is resized in Android to height 240; the search only examines the bottom-right UI.
// Textures are taken from a user-supplied battle screenshot; no game memory accessed.
const GEAR_W:usize=29;const GEAR_H:usize=32;
const WIN_W:usize=31;const WIN_H:usize=15;
fn ncc(frame:&[f32],fw:usize,fh:usize,templ:&[i8],tw:usize,th:usize,x:usize,y:usize)->f32{
    if x+tw>fw || y+th>fh || templ.len()!=tw*th{return -1.0}
    let mut sum_t=0.0f64;let mut sum_i=0.0f64;let mut sum_t2=0.0f64;let mut sum_i2=0.0f64;let mut sum_ti=0.0f64;
    for row in 0..th{
        for col in 0..tw{
            let a=templ[row*tw+col] as u8 as f64;
            let b=frame[(y+row)*fw+x+col] as f64;
            sum_t+=a;sum_i+=b;sum_t2+=a*a;sum_i2+=b*b;sum_ti+=a*b;
        }
    }
    let n=(tw*th) as f64;
    let numerator=sum_ti-sum_t*sum_i/n;
    let vt=sum_t2-sum_t*sum_t/n;
    let vi=sum_i2-sum_i*sum_i/n;
    if vt<1e-4||vi<1e-4{return -1.0}
    (numerator/(vt*vi).sqrt()).clamp(-1.0,1.0) as f32
}
fn locate_pair(frame:&[u32],width:usize,height:usize,gear:&[i8],win:&[i8])->[f32;6]{
    let mut result=[-1.0,-1.0,-1.0,-1.0,-1.0,-1.0];
    if height!=240||width<320||width>1200||frame.len()!=width*height
        ||gear.len()!=GEAR_W*GEAR_H||win.len()!=WIN_W*WIN_H{return result;}
    let img:Vec<f32>=frame.iter().map(|argb|{
        let r=((argb>>16)&0xff)as f32;let g=((argb>>8)&0xff)as f32;let b=(argb&0xff)as f32;
        // PIL 'L' luminance transform used to encode reference templates
        (r*0.299+g*0.587+b*0.114).round()
    }).collect();
    let left=width.saturating_sub(220);
    let right=(width.saturating_sub(60)).min(width-GEAR_W);
    let bottom=216.min(height-GEAR_H);
    let mut best=(-2.0f32,0usize,0usize);
    for y in (146..=bottom).step_by(2){
        for x in (left..=right).step_by(2){
            let s=ncc(&img,width,height,gear,GEAR_W,GEAR_H,x,y);
            if s>best.0{best=(s,x,y)}
        }
    }
    // Refine near peak at full resolution to avoid two-pixel error.
    let mut refined=best;
    for y in best.2.saturating_sub(2)..=(best.2+2).min(bottom){
        for x in best.1.saturating_sub(2)..=(best.1+2).min(right){
            let s=ncc(&img,width,height,gear,GEAR_W,GEAR_H,x,y);
            if s>refined.0{refined=(s,x,y)}
        }
    }
    result[0]=refined.1 as f32+GEAR_W as f32/2.0;
    result[1]=refined.2 as f32+GEAR_H as f32/2.0;
    result[4]=refined.0;
    // Reference screenshot: win button is 29 normalized px to the right of gear, 1 px down.
    let expected_x=refined.1+37;
    let expected_y=refined.2+5;
    let mut best_win=(-2.0f32,0usize,0usize);
    for y in expected_y.saturating_sub(12)..=(expected_y+12).min(height-WIN_H){
        for x in expected_x.saturating_sub(12)..=(expected_x+12).min(width-WIN_W){
            let s=ncc(&img,width,height,win,WIN_W,WIN_H,x,y);
            if s>best_win.0{best_win=(s,x,y)}
        }
    }
    result[2]=best_win.1 as f32+WIN_W as f32/2.0;
    result[3]=best_win.2 as f32+WIN_H as f32/2.0;
    result[5]=best_win.0;
    result
}
#[cfg(target_os="android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_ashkite_limbusauto_NativeVision_findPair(
    mut env:jni::JNIEnv,
    _class:jni::objects::JClass,
    pixels:jni::objects::JIntArray,
    width:jni::sys::jint,
    height:jni::sys::jint,
    gear:jni::objects::JByteArray,
    win:jni::objects::JByteArray,
)->jni::sys::jfloatArray{
    let fail=[-1.0f32;6];
    let response=(||->Option<[f32;6]>{
        if width<320||width>1200||height!=240{return None;}
        let len=env.get_array_length(&pixels).ok()? as usize;
        if len!=width as usize*height as usize{return None;}
        let mut pix=vec![0i32;len];
        env.get_int_array_region(&pixels,0,&mut pix).ok()?;
        let mut g=vec![0i8;GEAR_W*GEAR_H];
        let mut w=vec![0i8;WIN_W*WIN_H];
        env.get_byte_array_region(&gear,0,&mut g).ok()?;
        env.get_byte_array_region(&win,0,&mut w).ok()?;
        let converted:Vec<u32>=pix.into_iter().map(|v|v as u32).collect();
        Some(locate_pair(&converted,width as usize,height as usize,&g,&w))
    })().unwrap_or(fail);
    let Ok(out)=env.new_float_array(6)else{return std::ptr::null_mut()};
    if env.set_float_array_region(&out,0,&response).is_err(){return std::ptr::null_mut();}
    out.into_raw()
}
#[cfg(test)]
mod template_tests{
    use super::*;
    #[test]fn constant_patch_cannot_match(){let f=vec![90.0f32;240*432];let t=vec![55i8;29*32];assert!(ncc(&f,432,240,&t,29,32,280,173)<0.0);}
    #[test]fn bounding_checks(){let p=vec![0xff000000u32;240*432];let g=vec![0i8;29*32];let w=vec![0i8;31*15];let r=locate_pair(&p,432,240,&g,&w);assert!(r[4]<=0.0);}
}


// v0.5 finds the text-button panel in either idle or win-selected state.
// This anchor is stable even as the adjacent START gear changes appearance.
// The reference assets are normalized to a 240-pixel-high landscape screenshot.
const PANEL_W:usize=15;
const PANEL_H:usize=29;
fn refined_panel(gray:&[f32],fw:usize,fh:usize,templ:&[i8])->(f32,usize,usize){
    let min_x=fw.saturating_sub(185);let max_x=fw.saturating_sub(20).min(fw-PANEL_W);
    let min_y=145usize;let max_y=206usize.min(fh-PANEL_H);
    let mut best=(-2f32,min_x,min_y);
    for y in (min_y..=max_y).step_by(2){
        for x in (min_x..=max_x).step_by(2){
            let score=ncc(gray,fw,fh,templ,PANEL_W,PANEL_H,x,y);
            if score>best.0 {best=(score,x,y);}
        }
    }
    let previous=best;
    for y in previous.2.saturating_sub(2).max(min_y)..=(previous.2+2).min(max_y){
        for x in previous.1.saturating_sub(2).max(min_x)..=(previous.1+2).min(max_x){
            let score=ncc(gray,fw,fh,templ,PANEL_W,PANEL_H,x,y);
            if score>best.0 {best=(score,x,y);}
        }
    }
    best
}
fn locate_panel_controls(pixels:&[u32],width:usize,height:usize,idle:&[i8],selected:&[i8])->[f32;7]{
    let invalid=[-1f32;7];
    if height!=240||width<320||width>1200||pixels.len()!=width*height||
       idle.len()!=PANEL_W*PANEL_H||selected.len()!=PANEL_W*PANEL_H{return invalid;}
    let gray:Vec<f32>=pixels.iter().map(|c|{
        (((c>>16)&255) as f32*0.299+((c>>8)&255) as f32*0.587+
        (c&255) as f32*0.114).round()
    }).collect();
    let a=refined_panel(&gray,width,height,idle);
    let b=refined_panel(&gray,width,height,selected);
    // Idle and selected panels are mutually exclusive; the inactive model may
    // match an unrelated location. Use the stronger state alone for anchoring.
    let (x,y)=if b.0>a.0 {(b.1,b.2)}else{(a.1,a.2)};
    // Correlations at this single shared anchor remain inspectable even if weak.
    // Android gates ALL gestures on score, state and orange-gear confirmation.
    let aligned_idle=ncc(&gray,width,height,idle,PANEL_W,PANEL_H,x,y);
    let aligned_selected=ncc(&gray,width,height,selected,PANEL_W,PANEL_H,x,y);
    // Relative gear location from two different screenshots with different UI widths.
    let gx=x as isize-41;let gy=y as isize+11;
    if gx<6||gy<6||gx+6>=width as isize||gy+6>=height as isize{return invalid;}
    let mut bright=0usize;
    for dy in -5..=5 {
        for dx in -5..=5 {
            let p=pixels[(gy+dy)as usize*width+(gx+dx)as usize];
            let r=((p>>16)&255) as f32;
            let g=((p>>8)&255) as f32;
            let bl=(p&255) as f32;
            if r>205.0&&g>95.0&&g<236.0&&bl<155.0&&r>g*1.14{bright+=1;}
        }
    }
    [x as f32+1.0,y as f32+7.0,gx as f32,gy as f32,aligned_idle,aligned_selected,bright as f32/121.0]
}
#[cfg(target_os="android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_ashkite_limbusauto_NativeVision_locateControls(
    mut env:jni::JNIEnv,_class:jni::objects::JClass,
    pixels:jni::objects::JIntArray,width:jni::sys::jint,height:jni::sys::jint,
    idle:jni::objects::JByteArray,ready:jni::objects::JByteArray
)->jni::sys::jfloatArray{
    let result=(||->Option<[f32;7]>{
        if width<320||width>1200||height!=240{return None;}
        if env.get_array_length(&pixels).ok()? as usize!=width as usize*height as usize{return None;}
        if env.get_array_length(&idle).ok()? as usize!=PANEL_W*PANEL_H||
           env.get_array_length(&ready).ok()? as usize!=PANEL_W*PANEL_H{return None;}
        let mut a=vec![0i8;PANEL_W*PANEL_H];
        let mut b=vec![0i8;PANEL_W*PANEL_H];
        let mut p=vec![0i32;width as usize*height as usize];
        env.get_byte_array_region(&idle,0,&mut a).ok()?;
        env.get_byte_array_region(&ready,0,&mut b).ok()?;
        env.get_int_array_region(&pixels,0,&mut p).ok()?;
        let argb:Vec<u32>=p.into_iter().map(|v|v as u32).collect();
        Some(locate_panel_controls(&argb,width as usize,height as usize,&a,&b))
    })().unwrap_or([-1f32;7]);
    let Ok(output)=env.new_float_array(7)else{return std::ptr::null_mut();};
    if env.set_float_array_region(&output,0,&result).is_err(){return std::ptr::null_mut();}
    output.into_raw()
}
#[cfg(test)]
mod control_test {
    use super::*;
    #[test]fn bad_size_is_rejected(){
        let src=vec![0xff000000u32;240*520];
        assert_eq!(locate_panel_controls(&src,520,240,&[],&[])[0],-1.0);
    }
    #[test]fn selected_and_idle_template_differ_at_other_places(){
        let width=520usize;let height=240usize;
        let mut pixels=vec![0xff101010u32;width*height];
        let idle:Vec<i8>=(0..PANEL_W*PANEL_H)
            .map(|i| ((i*31+(i/15)*17)%180+20) as u8 as i8).collect();
        let selected:Vec<i8>=(0..PANEL_W*PANEL_H)
            .map(|i| ((i*11+(i/15)*41)%160+30) as u8 as i8).collect();
        for row in 0..PANEL_H {
            for col in 0..PANEL_W {
                let c=idle[row*PANEL_W+col] as u8 as u32;
                pixels[(170+row)*width+400+col]=0xff000000|(c<<16)|(c<<8)|c;
            }
        }
        let out=locate_panel_controls(&pixels,width,height,&idle,&selected);
        assert!((out[0]-401.0).abs()<=2.0);
        assert!(out[4]>0.0, "unexpected best match score: {}", out[4]);
    }
    #[test]fn constant_frame_is_rejected(){
        let src=vec![0xff080808u32;240*520];
        let idle=vec![7i8;PANEL_W*PANEL_H];
        let selected=vec![8i8;PANEL_W*PANEL_H];
        let candidate=locate_panel_controls(&src,520,240,&idle,&selected);
        // We now intentionally return a candidate for diagnostics;
        // a flat frame must have no recognized confident pattern.
        assert!(candidate[4]<0.0 && candidate[5]<0.0);
    }
}
