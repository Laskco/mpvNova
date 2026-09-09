use anitomy_ng::{parse, Options};
use jni::objects::{JClass, JObjectArray, JString};
use jni::refs::IntoAuto;
use jni::sys::jobjectArray;
use jni::{EnvUnowned, Outcome};
use std::ptr;

const MAX_FILENAME_CHARS: usize = 1024;

// Preserve duplicate fields such as episode ranges. Java owns the returned array;
// Rust allocations and temporary JNI references stay local to this call.
#[no_mangle]
pub extern "system" fn Java_app_mpvnova_player_AnitomyNg_parseNative(
    mut unowned_env: EnvUnowned,
    _class: JClass,
    input: JString,
) -> jobjectArray {
    let outcome = unowned_env.with_env(|env| -> jni::errors::Result<jobjectArray> {
        let filename = input.try_to_string(env)?;
        if filename.chars().count() > MAX_FILENAME_CHARS {
            return Ok(ptr::null_mut());
        }
        let elements = parse(&filename, Options::default());
        let result = JObjectArray::<JString>::new(env, elements.len() * 2, JString::null())?;
        for (index, element) in elements.iter().enumerate() {
            let kind = env.new_string(element.kind.as_str())?.auto();
            let value = env.new_string(&element.value)?.auto();
            result.set_element(env, index * 2, &kind)?;
            result.set_element(env, index * 2 + 1, &value)?;
        }
        Ok(result.into_raw())
    });
    match outcome.into_outcome() {
        Outcome::Ok(result) => result,
        Outcome::Err(_) | Outcome::Panic(_) => ptr::null_mut(),
    }
}
