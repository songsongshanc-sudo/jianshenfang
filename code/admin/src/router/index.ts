import { createRouter, createWebHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import AdminLayout from "../layouts/AdminLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import StoresView from "../views/StoresView.vue";
import MyStoreView from "../views/MyStoreView.vue";
import AccountsView from "../views/AccountsView.vue";
import BannersView from "../views/BannersView.vue";
import NoticesView from "../views/NoticesView.vue";
import AgreementsView from "../views/AgreementsView.vue";
import ConfigView from "../views/ConfigView.vue";
import CardsView from "../views/CardsView.vue";
import OrdersView from "../views/OrdersView.vue";
import GatesView from "../views/GatesView.vue";
import DoorsView from "../views/DoorsView.vue";
import TicketsView from "../views/TicketsView.vue";
import OpsView from "../views/OpsView.vue";
import CoachesView from "../views/CoachesView.vue";
import { useSessionStore } from "../stores/session";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView },
    {
      path: "/",
      component: AdminLayout,
      children: [
        { path: "", component: DashboardView },
        { path: "stores", component: StoresView },
        { path: "my-store", component: MyStoreView },
        { path: "accounts", component: AccountsView },
        { path: "banners", component: BannersView },
        { path: "notices", component: NoticesView },
        { path: "agreements", component: AgreementsView },
        { path: "configs", component: ConfigView },
        { path: "cards", component: CardsView },
        { path: "orders", component: OrdersView },
        { path: "gates", component: GatesView },
        { path: "doors", component: DoorsView },
        { path: "repairs", component: TicketsView },
        { path: "complaints", component: TicketsView },
        { path: "lost", component: TicketsView },
        { path: "equipment", component: OpsView },
        { path: "groupon", component: OpsView },
        { path: "coaches", component: CoachesView },
        { path: "franchise", component: OpsView },
        { path: "messages", component: OpsView },
        { path: "violations", component: OpsView }
      ]
    }
  ]
});

router.beforeEach((to) => {
  const session = useSessionStore();
  if (to.path !== "/login" && !session.token) {
    return "/login";
  }
  if (to.path === "/login" && session.token) {
    return "/";
  }
  const masterOnly = ["/stores", "/accounts", "/banners", "/notices", "/agreements", "/configs", "/equipment", "/franchise", "/messages", "/violations"];
  if (session.role === "STORE" && masterOnly.includes(to.path)) {
    return "/";
  }
  return true;
});
