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
    <p v-if="error" class="error">{{ error }}</p>
    <div ref="mapEl" class="map"></div>
    <p class="summary">{{ summary || "点击地图，或搜索地址。省、市、地址和经纬度会填进表单，还可以再改。" }}</p>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";

interface PlacePick {
  province: string;
  city: string;
  address: string;
  longitude: string;
  latitude: string;
}

interface AMapLngLat {
  getLng?: () => number;
  getLat?: () => number;
  lng?: number;
  lat?: number;
}

interface AMapMap {
  on: (event: string, handler: (event: { lnglat: AMapLngLat }) => void) => void;
  setZoomAndCenter: (zoom: number, center: [number, number]) => void;
  resize: () => void;
  add: (overlay: unknown) => void;
}

interface AMapMarker {
  setPosition: (position: [number, number]) => void;
}

interface AMapNamespace {
  Map: new (el: HTMLElement, options: { zoom: number; center: [number, number] }) => AMapMap;
  Marker: new (options: { position: [number, number] }) => AMapMarker;
  Geocoder: new () => {
    getAddress: (position: [number, number], callback: (status: string, result: RegeoResult) => void) => void;
  };
  PlaceSearch: new (options: { pageSize: number }) => {
    search: (keyword: string, callback: (status: string, result: SearchResult) => void) => void;
  };
  plugin: (names: string[], callback: () => void) => void;
}

interface RegeoResult {
  regeocode?: {
    formattedAddress?: string;
    addressComponent?: {
      province?: string | string[];
      city?: string | string[];
      district?: string | string[];
      township?: string | string[];
      street?: string | string[];
      streetNumber?: string | string[];
    };
  };
}

interface SearchResult {
  poiList?: {
    pois?: Array<{
      name?: string;
      pname?: string;
      cityname?: string;
      adname?: string;
      address?: string;
      location?: AMapLngLat;
    }>;
  };
}

const visible = defineModel<boolean>("visible", { required: true });
const props = defineProps<{ longitude: string; latitude: string }>();
const emit = defineEmits<{ pick: [value: PlacePick] }>();

const mapEl = ref<HTMLElement>();
const keyword = ref("");
const hits = ref<PlacePick[]>([]);
const summary = ref("");
const error = ref("");
let amap: AMapNamespace | null = null;
let map: AMapMap | null = null;
let marker: AMapMarker | null = null;
let geocoder: InstanceType<AMapNamespace["Geocoder"]> | null = null;
let placeSearch: InstanceType<AMapNamespace["PlaceSearch"]> | null = null;

function openMap() {
  if (!mapEl.value) {
    return;
  }
  if (!map) {
    void boot();
    return;
  }
  const center = startCenter();
  map.setZoomAndCenter(center.zoom, center.point);
  if (center.marked) {
    moveMarker(center.point);
  }
  window.setTimeout(() => map?.resize(), 80);
}

async function boot() {
  error.value = "";
  try {
    amap = await loadAmap();
    if (!mapEl.value || !amap) {
      return;
    }
    const center = startCenter();
    map = new amap.Map(mapEl.value, { zoom: center.zoom, center: center.point });
    map.on("click", (event) => {
      const point = pair(event.lnglat);
      if (point) {
        void reverseAt(point);
      }
    });
    await plugins();
    if (center.marked) {
      moveMarker(center.point);
    }
    window.setTimeout(() => map?.resize(), 80);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "高德地图加载失败，请手填经纬度";
  }
}

function plugins() {
  return new Promise<void>((resolve) => {
    if (!amap) {
      resolve();
      return;
    }
    amap.plugin(["AMap.Geocoder", "AMap.PlaceSearch"], () => {
      if (!amap) {
        resolve();
        return;
      }
      geocoder = new amap.Geocoder();
      placeSearch = new amap.PlaceSearch({ pageSize: 5 });
      resolve();
    });
  });
}

function startCenter() {
  const latitude = Number(props.latitude);
  const longitude = Number(props.longitude);
  if (Number.isFinite(latitude) && Number.isFinite(longitude) && (latitude !== 0 || longitude !== 0)) {
    return { point: [longitude, latitude] as [number, number], zoom: 16, marked: true };
  }
  return { point: [104, 35] as [number, number], zoom: 4, marked: false };
}

function reverseAt(point: [number, number]) {
  moveMarker(point);
  if (!geocoder) {
    error.value = "地址解析还没准备好，请再点一次";
    return;
  }
  geocoder.getAddress(point, (status, result) => {
    if (status !== "complete" || !result.regeocode) {
      error.value = "没有解析到这个位置，请换个点或手填";
      return;
    }
    error.value = "";
    const component = result.regeocode.addressComponent;
    const province = text(component?.province);
    const city = text(component?.city) || province;
    const address = [text(component?.district), text(component?.township), text(component?.street), text(component?.streetNumber)]
      .filter(Boolean)
      .join("") || text(result.regeocode.formattedAddress);
    apply({
      province,
      city,
      address,
      longitude: String(point[0]),
      latitude: String(point[1])
    });
  });
}

function search() {
  if (!placeSearch) {
    error.value = "地图还没加载完";
    return;
  }
  placeSearch.search(keyword.value, (status, result) => {
    const pois = result.poiList?.pois || [];
    hits.value = pois.flatMap((poi) => {
      const point = poi.location ? pair(poi.location) : null;
      if (!point) {
        return [];
      }
      const province = poi.pname || "";
      const city = poi.cityname || province;
      return [{
        province,
        city,
        address: `${poi.adname || ""}${poi.address || ""}${poi.name || ""}`,
        longitude: String(point[0]),
        latitude: String(point[1])
      }];
    });
    if (status !== "complete" || hits.value.length === 0) {
      error.value = "没有搜到这个地址";
      return;
    }
    error.value = "";
    const first = hits.value[0];
    if (first) {
      choose(first);
    }
  });
}

function choose(place: PlacePick) {
  const point: [number, number] = [Number(place.longitude), Number(place.latitude)];
  map?.setZoomAndCenter(16, point);
  reverseAt(point);
}

function apply(place: PlacePick) {
  summary.value = `${place.province} ${place.city} ${place.address}`;
  emit("pick", place);
}

function moveMarker(point: [number, number]) {
  if (!map || !amap) {
    return;
  }
  if (!marker) {
    marker = new amap.Marker({ position: point });
    map.add(marker);
  } else {
    marker.setPosition(point);
  }
}

function pair(value: AMapLngLat): [number, number] | null {
  const longitude = value.getLng ? value.getLng() : value.lng;
  const latitude = value.getLat ? value.getLat() : value.lat;
  if (longitude == null || latitude == null || !Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    return null;
  }
  return [longitude, latitude];
}

function text(value: string | string[] | undefined) {
  if (Array.isArray(value)) {
    return value[0] || "";
  }
  return value || "";
}

let loading: Promise<AMapNamespace> | null = null;

function loadAmap() {
  const key = import.meta.env.VITE_AMAP_KEY;
  const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_JS_CODE;
  if (!key || !securityJsCode) {
    return Promise.reject(new Error("还没有配置高德地图 Key。可以先手填经纬度。"));
  }
  const cached = (window as Window & { AMap?: AMapNamespace }).AMap;
  if (cached) {
    return Promise.resolve(cached);
  }
  if (loading) {
    return loading;
  }
  window._AMapSecurityConfig = { securityJsCode };
  loading = new Promise((resolve, reject) => {
    const finish = (value: AMapNamespace) => {
      (window as Window & { AMap?: AMapNamespace }).AMap = value;
      resolve(value);
    };
    const fail = (reason: unknown) => {
      loading = null;
      const detail = reason instanceof Error ? reason.message : "";
      reject(new Error(detail || "高德地图加载失败。请确认 Key 已开通 Web端(JS API)，并且域名包含当前网址。"));
    };
    const start = () => {
      if (!window.AMapLoader) {
        fail(new Error("高德地图脚本加载失败"));
        return;
      }
      window.AMapLoader.load({ key, version: "2.0" }).then((loaded) => finish(loaded as AMapNamespace)).catch(fail);
    };
    if (window.AMapLoader) {
      start();
      return;
    }
    const script = document.createElement("script");
    script.src = "https://webapi.amap.com/loader.js";
    script.onload = start;
    script.onerror = () => fail(new Error("高德地图脚本加载失败"));
    document.head.appendChild(script);
  });
  return loading;
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
.summary,
.error {
  margin: 10px 0 0;
  font-size: 13px;
}
.summary {
  color: #78716c;
}
.error {
  color: #b91c1c;
}
</style>
