const https = require("https");
const worker = "https://falling-wildflower-9a91.samdem612.workers.dev";
const key = "kKRUiaCTetZ58DRFpFpTuVnYJjH6E7mg";

function get(targetUrl) {
  return new Promise((resolve, reject) => {
    const u = worker + "/?url=" + encodeURIComponent(targetUrl) + "&k=" + key;
    https.get(u, {
      headers: {
        "x-proxy-key": key,
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
      }
    }, res => {
      let data = "";
      res.on("data", chunk => data += chunk);
      res.on("end", () => resolve({ status: res.statusCode, data }));
    }).on("error", reject);
  });
}

async function test() {
  const p = await get("https://v6.olamovies.mov/movies/inception-2010");
  console.log("Movie status:", p.status, "len:", p.data.length);
  const regex = /https?:\/\/[^\s"'\\]*(?:ol-am\.top|hubcloud|hubdrive|gofile|drive\.google)[^\s"'\\]*/g;
  let m;
  let count = 0;
  while ((m = regex.exec(p.data)) !== null && count < 10) {
    console.log("Link:", m[0]);
    count++;
  }
}
test();
