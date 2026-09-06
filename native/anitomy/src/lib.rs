use anitomy_ng::{parse, Options};
use jni::objects::{JClass, JString};
use jni::sys::jobjectArray;
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

const MAX_FILENAME_CHARS: usize = 1024;

// Preserve duplicate fields such as episode ranges. Java owns the returned array;
// Rust allocations and temporary JNI references stay local to this call.
#[no_mangle]
pub extern "system" fn Java_app_mpvnova_player_AnitomyNg_parseNative(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jobjectArray {
    catch_unwind(AssertUnwindSafe(|| -> jni::errors::Result<jobjectArray> {
        let filename: String = env.get_string(&input)?.into();
        if filename.chars().count() > MAX_FILENAME_CHARS {
            return Ok(ptr::null_mut());
        }
        let elements = parse(&filename, Options::default());
        let result = env.new_object_array((elements.len() * 2) as i32, "java/lang/String", JString::default())?;
        for (index, element) in elements.iter().enumerate() {
            let kind = env.auto_local(env.new_string(element.kind.as_str())?);
            let value = env.auto_local(env.new_string(&element.value)?);
            env.set_object_array_element(&result, (index * 2) as i32, &kind)?;
            env.set_object_array_element(&result, (index * 2 + 1) as i32, &value)?;
        }
        Ok(result.into_raw())
    }))
    .ok()
    .and_then(Result::ok)
    .unwrap_or(ptr::null_mut())
}
