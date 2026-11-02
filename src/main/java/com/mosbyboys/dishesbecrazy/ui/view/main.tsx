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
import RestaurantOrdersPage from "./orders/RestaurantOrdersPage";
import RestaurantsPage from "./menu/RestaurantsPage";
import MenuPage from "./menu/MenuPage";
import {CartProvider} from "./CartContext";
import CartPage from "./menu/CartPage";
import OrderSummaryPage from "./orders/OrderSummaryPage";

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
                            <ProtectedRoute allowedRoles={["CUSTOMER", "COURIER", "OPERATOR"]}>
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
                        path="/restaurant/orders"
                        element={
                            <ProtectedRoute allowedRoles={["RESTAURANT"]}>
                                <RestaurantOrdersPage/>
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
                            <OrderSummaryPage />
                        }
                    />
                </Routes>
                <OutputConsole/>
            </CartProvider>
        </AuthProvider>
    </BrowserRouter>
);
