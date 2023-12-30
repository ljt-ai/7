use catch_panic::catch_panic;
use get_vdom_first_element_by_class_name;
use jni_fn::jni_fn;
use jnix::jni::objects::{JByteBuffer, JClass};
use jnix::jni::sys::jint;
use jnix::jni::JNIEnv;
use parse_marshal_inplace;
use serde::Serialize;
use tl::Parser;
use tl::VDom;

#[allow(non_snake_case)]
#[derive(Serialize)]
struct Limits {
    current: i32,
    maximum: i32,
    resetCost: i32,
}

fn parse_limit(dom: &VDom, parser: &Parser) -> Option<Limits> {
    let iter = get_vdom_first_element_by_class_name(dom, "homebox")?
        .as_tag()?
        .query_selector(parser, "strong")?;
    let vec: Vec<i32> = iter
        .filter_map(|e| e.get(parser)?.inner_text(parser).parse::<i32>().ok())
        .collect();
    Some(Limits {
        current: vec[0],
        maximum: vec[1],
        resetCost: vec[2],
    })
}

#[no_mangle]
#[catch_panic]
#[allow(non_snake_case)]
#[jni_fn("com.hippo.ehviewer.client.parser.HomeParserKt")]
pub fn parseLimit(env: JNIEnv, _class: JClass, input: JByteBuffer, limit: jint) -> jint {
    parse_marshal_inplace(&env, input, limit, |dom, parser, _| {
        parse_limit(dom, parser)
    })
}
