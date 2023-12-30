use catch_panic::catch_panic;
use jni_fn::jni_fn;
use jnix::jni::objects::{JByteBuffer, JClass};
use jnix::jni::sys::jint;
use jnix::jni::JNIEnv;
use quick_xml::escape::unescape;
use serde::Serialize;
use tl::{Parser, VDom};
use {parse_marshal_inplace, regex};

#[derive(Serialize)]
struct Torrent {
    posted: String,
    size: String,
    seeds: i32,
    peers: i32,
    downloads: i32,
    url: String,
    name: String,
}

fn parse_torrent_list(dom: &VDom, parser: &Parser) -> Option<Vec<Torrent>> {
    let list = dom.query_selector("table")?.filter_map(|e| {
        let html = e.get(parser)?.inner_html(parser);
        if html.contains("Expunged") {
            None
        } else {
            let grp = regex!("</span> ([0-9-]+) [0-9:]+</td>[\\s\\S]+</span> ([0-9.]+ [KMGT]iB)</td>[\\s\\S]+</span> ([0-9]+)</td>[\\s\\S]+</span> ([0-9]+)</td>[\\s\\S]+</span> ([0-9]+)</td>[\\s\\S]+</span>([^<]+)</td>[\\s\\S]+onclick=\"document.location='([^\"]+)'[^<]+>([^<]+)</a>").captures(&html).unwrap();
            let name = unescape(&grp[8]).ok()?;
            Some(Torrent {
                posted: grp[1].to_string(),
                size: grp[2].to_string(),
                seeds: grp[3].parse().ok()?,
                peers: grp[4].parse().ok()?,
                downloads: grp[5].parse().ok()?,
                url: grp[7].to_string(),
                name: name.to_string()
            })
        }
    }).collect::<Vec<Torrent>>();
    Some(list)
}

#[no_mangle]
#[catch_panic]
#[allow(non_snake_case)]
#[jni_fn("com.hippo.ehviewer.client.parser.TorrentParserKt")]
pub fn parseTorrent(env: JNIEnv, _class: JClass, buffer: JByteBuffer, limit: jint) -> jint {
    parse_marshal_inplace(&env, buffer, limit, |dom, parser, _html| {
        parse_torrent_list(dom, parser)
    })
}
