const ids = ["9766575547322232", "9766576943805313", "9766586536489391", "11324857283680755"];
(async () => {
  for (const id of ids) {
    const r = await fetch("https://www.showdoc.com.cn/server/index.php?s=/api/page/info&page_id=" + id, {
      headers: { Referer: "https://www.showdoc.com.cn/szeasco/" + id }
    });
    const j = await r.json();
    const md = (j.data && j.data.page_content) || "";
    const plain = md.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/<[^>]+>/g, "");
    console.log("\n===== " + id + " " + ((j.data && j.data.page_title) || "") + " =====");
    console.log(plain.slice(0, 2500));
  }
})();
