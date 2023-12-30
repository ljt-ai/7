use jni_fn::jni_fn;
use jnix::jni::objects::{JByteBuffer, JClass};
use jnix::jni::sys::jint;
use jnix::jni::JNIEnv;
use parse_marshal_inplace;
use parser::list::{parse_info_list, GalleryListResult};
use quick_xml::escape::unescape;
use serde::Serialize;
use tl::Parser;
use tl::VDom;

#[derive(Serialize)]
#[allow(non_snake_case)]
struct FavResult {
    catArray: Vec<String>,
    countArray: Vec<i32>,
    galleryListResult: GalleryListResult,
}

fn parse_fav(dom: &VDom, parser: &Parser, html: &str) -> Option<FavResult> {
    if html.contains("This page requires you to log on.</p>") {
        panic!("Not logged in!")
    }
    let vec: Vec<(String, i32)> = dom
        .get_elements_by_class_name("fp")
        .enumerate()
        .filter_map(|(i, e)| {
            if i == 10 {
                return None;
            }
            let top = e.get(parser)?.children()?;
            let children = top.top();
            let cat = children[5].get(parser)?.inner_text(parser);
            let name = unescape(&cat).ok()?.trim().to_string();
            let count = children[1]
                .get(parser)?
                .inner_text(parser)
                .parse::<i32>()
                .ok()?;
            Some((name, count))
        })
        .collect();
    if vec.len() == 10 {
        let list = parse_info_list(dom, parser, html)?;
        let cat = vec.iter().cloned().unzip();
        Some(FavResult {
            catArray: cat.0,
            countArray: cat.1,
            galleryListResult: list,
        })
    } else {
        None
    }
}

#[no_mangle]
#[allow(non_snake_case)]
#[jni_fn("com.hippo.ehviewer.client.parser.FavoritesParserKt")]
pub fn parseFav(env: JNIEnv, _class: JClass, input: JByteBuffer, limit: jint) -> jint {
    parse_marshal_inplace(&env, input, limit, |dom, parser, html| {
        parse_fav(dom, parser, html)
    })
}
