import {createRoot} from "react-dom/client";
import "./styles/base.css";
import OutputConsole from "./components/OutputConsole";
import AppScroll from "./components/AppScroll";
import {AuthProvider} from "./context/AuthContext";
import ProtectedRoute from "./ProtectedRoute";
import {
    BrowserRouter,
    Routes,
    Route,
    Navigate
} from "react-router-dom";

import LoginPage from "./LoginPage";
import RegistrationPage from "./RegistrationPage";
import HomePage from "./homePages/HomePage";
import RestaurantHomePage from "./homePages/RestaurantHomePage";
import CourierHomePage from "./homePages/CourierHomePage";
import RestaurantOrdersPage from "./orders/RestaurantOrdersPage";
import OrderHistoryPage from "./orders/OrderHistoryPage";
import LedgerPage from "./orders/LedgerPage";
import RestaurantsPage from "./menu/RestaurantsPage";
import MenuPage from "./menu/MenuPage";
import {CartProvider} from "./context/CartContext";
import CartPage from "./menu/CartPage";
import OrderSummaryPage from "./orders/OrderSummaryPage";
import DashboardPage from "./dashboard/DashboardPage";
import OperatorHomePage from "./homePages/OperatorHomePage";
import AddMenuItemPage from "./menu/AddMenuItemPage";
import EditMenuItemPage from "./menu/EditMenuItemPage";
import AlternatePath from "./easterEggs/AlternatePath";
import BackgroundRedirect from "./easterEggs/BackgroundRedirect";
import ScrollPage from "./menu/ScrollPage";
import PostMenuItemPage from "./menu/PostMenuItem";
import {NotificationProvider} from "./notifications/NotificationContext";
import ToastStack from "./notifications/ToastStack";

createRoot(document.getElementById("root")!).render(
    <BrowserRouter>
        <AuthProvider>
            <NotificationProvider>
            <CartProvider>
                <AppScroll>
                <Routes>
                    <Route path="/" element={<Navigate to="/login" replace/>}/>
                    <Route path="/login" element={<LoginPage/>}/>
                    <Route path="/register" element={<RegistrationPage/>}/>
                    <Route path="/mysunshine" element={<AlternatePath/>}/>
                    <Route path="/taco" element={<BackgroundRedirect/>}/>

                    <Route
                        path="/home"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                                <HomePage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurant/home"
                        element={
                            <ProtectedRoute allowedRoles={["RESTAURANT"]}>
                                <RestaurantHomePage/>
                            </ProtectedRoute>
                        }
                    />
                                        <Route
                        path="/courier/home"
                        element={
                            <ProtectedRoute allowedRoles={["COURIER"]}>
                                <CourierHomePage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/operator/home"
                        element={
                            <ProtectedRoute allowedRoles={["OPERATOR"]}>
                                <OperatorHomePage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurant/orders"
                        element={
                            <ProtectedRoute allowedRoles={["RESTAURANT"]}>
                                <RestaurantOrdersPage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurant/add"
                        element={
                            <ProtectedRoute allowedRoles={["RESTAURANT"]}>
                                <AddMenuItemPage />
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurant/edit"
                        element={
                            <ProtectedRoute allowedRoles={["RESTAURANT"]}>
                                <EditMenuItemPage />
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurant/post"
                        element={
                            <ProtectedRoute allowedRoles={["RESTAURANT"]}>
                                <PostMenuItemPage />
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/orders/history"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER", "RESTAURANT", "COURIER", "OPERATOR"]}>
                                <OrderHistoryPage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/ledger"
                        element={
                            <ProtectedRoute allowedRoles={["OPERATOR"]}>
                                <LedgerPage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurants"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                                <RestaurantsPage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/restaurants/:restaurantId/menu"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                                <MenuPage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/cart"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                                <CartPage/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/order-summary/:orderId"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                                <OrderSummaryPage />
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/dashboard"
                        element={
                            <ProtectedRoute
                                allowedRoles={[
                                    "CUSTOMER",
                                    "RESTAURANT",
                                    "COURIER",
                                    "OPERATOR"
                                ]}
                            >
                                <DashboardPage />
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/feed"
                        element={
                            <ProtectedRoute
                                allowedRoles={["CUSTOMER"]}
                            >
                                <ScrollPage />
                            </ProtectedRoute>
                        }
                    />
                </Routes>
                </AppScroll>
                <OutputConsole/>
            </CartProvider>
            </NotificationProvider>
        </AuthProvider>
    </BrowserRouter>
);
