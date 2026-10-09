<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style>
page {
  background: #f6f3ee;
  color: #1c1917;
  font-size: 28rpx;
}
button {
  background: #c2410c;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
  line-height: 88rpx;
  height: 88rpx;
}
button::after {
  border: none;
}
button[size="mini"] {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 24rpx;
  font-size: 24rpx;
  background: #fff7ed;
  color: #9a3412;
}
input,
textarea {
  box-sizing: border-box;
  background: #fff;
  border-radius: 20rpx;
  padding: 22rpx 24rpx;
  font-size: 28rpx;
  color: #1c1917;
}
</style>
