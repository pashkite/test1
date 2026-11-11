//! Small reusable Rust screen-patch comparison core.
//! Intentional: no network, game process access, or privileged APIs.

/// Return RGB similarity of two equally sized packed Java ARGB pixel slices.
/// 1.0 means identical, 0.0 is maximally different.
pub fn similarity_argb(left: &[u32], right: &[u32]) -> f32 {
    if left.len() != right.len() || left.is_empty() {
        return 0.0;
    }
    let mut error: u64 = 0;
    let mut count: u64 = 0;
    // Sample no more than ~2400 pixels for fast, bounded comparison.
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

#[cfg(target_os = "android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_dev_ashkite_limbusauto_NativeVision_similarity(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    left: jni::objects::JIntArray,
    right: jni::objects::JIntArray,
) -> jni::sys::jfloat {
    // A native failure means "no match"; never cause a tap on error.
    let (Ok(len_left), Ok(len_right)) = (
        env.get_array_length(&left),
        env.get_array_length(&right),
    ) else {
        return 0.0;
    };
    if len_left != len_right || len_left <= 0 || len_left > 200_000 {
        return 0.0;
    }
    let mut a = vec![0i32; len_left as usize];
    let mut b = vec![0i32; len_right as usize];
    if env.get_int_array_region(&left, 0, &mut a).is_err()
        || env.get_int_array_region(&right, 0, &mut b).is_err()
    {
        return 0.0;
    }
    let a: Vec<u32> = a.into_iter().map(|v| v as u32).collect();
    let b: Vec<u32> = b.into_iter().map(|v| v as u32).collect();
    similarity_argb(&a, &b)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn identical() { assert_eq!(similarity_argb(&[0xffaa5511; 100], &[0xffaa5511; 100]), 1.0); }
    #[test]
    fn differs() { assert!(similarity_argb(&[0xff000000; 100], &[0xffffffff; 100]) < 0.01); }
    #[test]
    fn mismatched_lengths() { assert_eq!(similarity_argb(&[1,2], &[1]), 0.0); }
    #[test]
    fn empty() { assert_eq!(similarity_argb(&[], &[]), 0.0); }
}
