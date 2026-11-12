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
