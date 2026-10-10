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
  background: #f5f6f8;
  color: #1a1a1a;
  font-size: 28rpx;
}
/* 盖过 uni-h5 默认 button.css；flex 居中 + 轻微底内边距修正中文光学偏下 */
uni-app uni-button,
button {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 100%;
  margin-left: 0;
  margin-right: 0;
  padding: 0 0 2rpx;
  height: 88rpx;
  line-height: 1.2;
  border: none;
  border-radius: 16rpx;
  background-color: #ea580c;
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
}
uni-app uni-button::after,
button::after {
  border: none;
}
uni-app uni-button[size="mini"],
button[size="mini"] {
  display: inline-flex;
  width: auto;
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  background-color: #fff4ed;
  color: #c2410c;
  font-size: 24rpx;
  line-height: 1.2;
}
input {
  box-sizing: border-box;
  width: 100%;
  height: 88rpx;
  min-height: 88rpx;
  padding: 0 24rpx;
  line-height: 88rpx;
  font-size: 28rpx;
  color: #1a1a1a;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 16rpx;
}
textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 200rpx;
  padding: 20rpx 24rpx;
  line-height: 44rpx;
  font-size: 28rpx;
  color: #1a1a1a;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 16rpx;
}
</style>
