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
import AddMenuItemPage from "./menu/AddMenuItemPage";
import EditMenuItemPage from "./menu/EditMenuItemPage";
import AlternatePath from "./AlternatePath";
import BackgroundRedirect from "./BackgroundRedirect";

createRoot(document.getElementById("root")!).render(
    <BrowserRouter>
        <AuthProvider>
            <CartProvider>
                <div className="app-scroll">
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
                </div>
                <OutputConsole/>
            </CartProvider>
        </AuthProvider>
    </BrowserRouter>
);
