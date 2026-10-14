/** 约束富文本大图，避免超出屏幕（rich-text 不吃外层 CSS） */
export function fitRichHtml(html: string | null | undefined): string {
  if (!html) {
    return "";
  }
  return html.replace(/<img\b([^>]*)>/gi, (_match, attrs: string) => {
    const cleaned = String(attrs)
      .replace(/\s*(width|height)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "")
      .replace(/\s*style\s*=\s*("[^"]*"|'[^']*')/gi, "");
    return `<img style="max-width:100%;width:100%;height:auto;display:block;"${cleaned}>`;
  });
}
