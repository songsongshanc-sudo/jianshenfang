<template>
  <div>
    <h1>订单</h1>
    <p class="lead">门店账号只能看本店订单。购买后不能退款。</p>
    <el-table :data="rows">
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column prop="storeName" label="门店" />
      <el-table-column prop="productName" label="卡种" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">{{ (row.amountFen / 100).toFixed(2) }} 元</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusText(row.status) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http, type ApiBody } from "../api/http";

interface OrderRow {
  id: string;
  orderNo: string;
  storeName: string;
  productName: string;
  amountFen: number;
  status: string;
}

const rows = ref<OrderRow[]>([]);

function statusText(status: string) {
  if (status === "PAID") return "已支付";
  if (status === "CLOSED") return "已关闭";
  if (status === "REFUNDED") return "已退款";
  return "待支付";
}

async function load() {
  const response = await http.get<ApiBody<OrderRow[]>>("/api/admin/orders");
  rows.value = response.data.data;
}

onMounted(load);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
