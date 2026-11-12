import {createRoot} from "react-dom/client";
import "./styles/base.css";
import OutputConsole from "./components/OutputConsole";
import {AuthProvider} from "./AuthContext";
import ProtectedRoute from "./ProtectedRoute";
import {
    BrowserRouter,
    Routes,
    Route,
    Navigate
} from "react-router-dom";

import LoginPage from "./LoginPage";
import RegistrationPage from "./RegistrationPage";
import HomePage from "./HomePage";
import RestaurantHomePage from "./RestaurantHomePage";
import CourierHomePage from "./CourierHomePage";
import RestaurantOrdersPage from "./orders/RestaurantOrdersPage";
import OrderHistoryPage from "./orders/OrderHistoryPage";
import RestaurantsPage from "./menu/RestaurantsPage";
import MenuPage from "./menu/MenuPage";
import {CartProvider} from "./CartContext";
import CartPage from "./menu/CartPage";
import OrderSummaryPage from "./orders/OrderSummaryPage";
import DashboardPage from "./dashboard/DashboardPage";
import OperatorHomePage from "./OperatorHomePage";

createRoot(document.getElementById("root")!).render(
    <BrowserRouter>
        <AuthProvider>
            <CartProvider>
                <Routes>
                    <Route path="/" element={<Navigate to="/login" replace/>}/>
                    <Route path="/login" element={<LoginPage/>}/>
                    <Route path="/register" element={<RegistrationPage/>}/>

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
                        path="/orders/history"
                        element={
                            <ProtectedRoute allowedRoles={["CUSTOMER", "RESTAURANT", "COURIER", "OPERATOR"]}>
                                <OrderHistoryPage/>
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
                </Routes>
                <OutputConsole/>
            </CartProvider>
        </AuthProvider>
    </BrowserRouter>
);
