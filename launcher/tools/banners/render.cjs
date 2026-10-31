// Rendert alle Banner als PNG nach launcher/public/news/ (node tools/banners/render.cjs)
const { chromium } = require("playwright");
const http = require("http"); const fs = require("fs"); const path = require("path");
const root = path.resolve(__dirname, "../..");
const types = { ".html": "text/html", ".js": "text/javascript", ".woff2": "font/woff2", ".map": "application/json" };
const server = http.createServer((req, res) => {
  const file = path.join(root, decodeURIComponent(req.url.split("?")[0]));
  fs.readFile(file, (err, data) => { if (err) { res.writeHead(404); return res.end(); } res.writeHead(200, { "Content-Type": types[path.extname(file)] || "application/octet-stream" }); res.end(data); });
}).listen(4399, async () => {
  const out = path.join(root, "public/news"); fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ args: ["--use-gl=swiftshader", "--enable-webgl", "--ignore-gpu-blocklist"] });
  const page = await browser.newPage({ viewport: { width: 640, height: 320 }, deviceScaleFactor: 2 });
  for (const b of ["friends", "modules", "hud", "badge", "version"]) {
    await page.goto(`http://localhost:4399/tools/banners/generator.html?b=${b}`);
    await page.waitForFunction(() => window.bannerReady === true, null, { timeout: 20000 });
    await page.locator("#banner").screenshot({ path: path.join(out, `${b}.png`) });
    console.log("✓", b);
  }
  await browser.close(); server.close();
});
