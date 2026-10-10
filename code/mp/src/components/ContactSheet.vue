<template>
  <view v-if="visible" class="mask" @click="close">
    <view class="sheet" @click.stop>
      <text class="title">联系客服</text>
      <text v-if="contacts?.nightHint" class="hint">{{ contacts.nightHint }}</text>
      <view v-for="phone in contacts?.servicePhones || []" :key="phone" class="line">
        <text>{{ shiftLabel }} {{ phone }}</text>
        <button size="mini" @click="call(phone)">拨打</button>
      </view>
      <view v-for="phone in contacts?.logisticsPhones || []" :key="'l' + phone" class="line">
        <text>后勤 {{ phone }}</text>
        <button size="mini" @click="call(phone)">拨打</button>
      </view>
      <text v-if="contacts?.complaintHint" class="hint">{{ contacts.complaintHint }}</text>
      <view v-for="phone in contacts?.complaintPhones || []" :key="'c' + phone" class="line">
        <text>投诉 {{ phone }}</text>
        <button size="mini" @click="call(phone)">拨打</button>
      </view>
      <text v-if="empty" class="hint">这家店还没有配置电话</text>
      <view class="cancel" @click="close">关闭</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Contacts } from "../api/catalog";

const props = defineProps<{ visible: boolean; contacts: Contacts | null }>();
const emit = defineEmits<{ close: [] }>();

const shiftLabel = computed(() => (props.contacts?.shift === "NIGHT" ? "夜班" : "白班"));
const empty = computed(() => {
  const data = props.contacts;
  if (!data) return false;
  return data.servicePhones.length === 0 && data.logisticsPhones.length === 0 && data.complaintPhones.length === 0;
});

function close() {
  emit("close");
}

function call(phone: string) {
  uni.makePhoneCall({ phoneNumber: phone });
}
</script>

<style scoped>
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 32rpx 48rpx;
  box-sizing: border-box;
}
.title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
}
.hint,
.line {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
}
.line {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cancel {
  margin-top: 28rpx;
  padding: 20rpx;
  text-align: center;
  color: #78716c;
}
</style>
