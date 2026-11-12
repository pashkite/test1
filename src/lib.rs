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
