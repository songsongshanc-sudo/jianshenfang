let pending = false;

export function isMember() {
  return uni.getStorageSync("mpTokenType") === "ACCESS";
}

export function clearSession() {
  uni.removeStorageSync("mpToken");
  uni.removeStorageSync("mpTokenType");
}

function currentRoute() {
  const pages = getCurrentPages();
  return pages[pages.length - 1]?.route || "";
}

/** 提示未登录，并打开注册页。已在注册页时只提示。 */
export function promptLogin() {
  uni.showToast({ title: "未登录", icon: "none" });
  if (pending || currentRoute() === "pages/login/login") {
    return;
  }
  pending = true;
  setTimeout(() => {
    uni.navigateTo({
      url: "/pages/login/login",
      complete() {
        pending = false;
      },
    });
  }, 400);
}
