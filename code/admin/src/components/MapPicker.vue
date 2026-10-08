<template>
  <el-dialog v-model="visible" title="选择门店位置" width="860px" append-to-body @opened="openMap">
    <div class="search">
      <el-input v-model="keyword" placeholder="搜索地址，例如深圳湾体育中心" @keyup.enter="search" />
      <el-button native-type="button" @click="search">搜索</el-button>
    </div>
    <div v-if="hits.length > 1" class="hits">
      <el-button v-for="(hit, index) in hits" :key="index" link native-type="button" @click="choose(hit)">
        {{ hit.city }} {{ hit.address }}
      </el-button>
    </div>
    <div ref="mapEl" class="map"></div>
    <p class="summary">{{ summary || "点击地图，或搜索地址。省、市、地址和经纬度会填进表单，还可以再改。" }}</p>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { http, type ApiBody } from "../api/http";
import { gcj02ToWgs84, wgs84ToGcj02 } from "../utils/gcj02";

interface PlacePick {
  province: string;
  city: string;
  address: string;
  longitude: string;
  latitude: string;
}

const visible = defineModel<boolean>("visible", { required: true });
const props = defineProps<{ longitude: string; latitude: string }>();
const emit = defineEmits<{ pick: [value: PlacePick] }>();

const mapEl = ref<HTMLElement>();
const keyword = ref("");
const hits = ref<PlacePick[]>([]);
const summary = ref("");
let map: L.Map | null = null;
let marker: L.CircleMarker | null = null;

function openMap() {
  if (!mapEl.value) {
    return;
  }
  if (!map) {
    map = L.map(mapEl.value);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap",
      maxZoom: 19
    }).addTo(map);
    map.on("click", (event: L.LeafletMouseEvent) => {
      void reverseAt(event.latlng.lat, event.latlng.lng);
    });
  }
  const center = startCenter();
  map.setView(center.point, center.zoom);
  if (center.marked) {
    moveMarker(center.point);
  }
  window.setTimeout(() => map?.invalidateSize(), 80);
}

function startCenter() {
  const latitude = Number(props.latitude);
  const longitude = Number(props.longitude);
  if (Number.isFinite(latitude) && Number.isFinite(longitude) && (latitude !== 0 || longitude !== 0)) {
    const wgs = gcj02ToWgs84(latitude, longitude);
    return { point: [wgs.latitude, wgs.longitude] as L.LatLngExpression, zoom: 16, marked: true };
  }
  return { point: [35, 104] as L.LatLngExpression, zoom: 4, marked: false };
}

async function reverseAt(wgsLatitude: number, wgsLongitude: number) {
  moveMarker([wgsLatitude, wgsLongitude]);
  const gcj = wgs84ToGcj02(wgsLatitude, wgsLongitude);
  const response = await http.get<ApiBody<PlaceBody>>("/api/admin/geo/reverse", {
    params: { latitude: gcj.latitude, longitude: gcj.longitude }
  });
  apply(toPick(response.data.data));
}

async function search() {
  const response = await http.get<ApiBody<PlaceBody[]>>("/api/admin/geo/search", {
    params: { keyword: keyword.value }
  });
  hits.value = response.data.data.map(toPick);
  if (hits.value[0]) {
    choose(hits.value[0]);
  }
}

function choose(place: PlacePick) {
  const wgs = gcj02ToWgs84(Number(place.latitude), Number(place.longitude));
  const point: L.LatLngExpression = [wgs.latitude, wgs.longitude];
  map?.setView(point, 16);
  moveMarker(point);
  apply(place);
}

function apply(place: PlacePick) {
  summary.value = `${place.province} ${place.city} ${place.address}`;
  emit("pick", place);
}

function toPick(place: PlaceBody): PlacePick {
  return {
    province: place.province,
    city: place.city,
    address: place.address,
    longitude: String(place.longitude),
    latitude: String(place.latitude)
  };
}

function moveMarker(point: L.LatLngExpression) {
  if (!map) {
    return;
  }
  if (!marker) {
    marker = L.circleMarker(point, {
      radius: 9,
      color: "#9a3412",
      weight: 2,
      fillColor: "#c2410c",
      fillOpacity: 0.95
    }).addTo(map);
  } else {
    marker.setLatLng(point);
  }
}

interface PlaceBody {
  province: string;
  city: string;
  address: string;
  longitude: number;
  latitude: number;
}
</script>

<style scoped>
.search {
  display: flex;
  gap: 8px;
}
.search :deep(.el-input) {
  flex: 1;
}
.hits {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 8px;
}
.map {
  height: 420px;
  margin-top: 12px;
  border-radius: 12px;
  overflow: hidden;
}
.summary {
  margin: 10px 0 0;
  color: #78716c;
  font-size: 13px;
}
</style>
