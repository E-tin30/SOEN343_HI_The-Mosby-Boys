import PageErrorBoundary from "./components/PageErrorBoundary";
import { installRuntimeLogging, logError } from "./outputLog";
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
import OrderHistoryPage from "./orders/OrderHistoryPage";
import RestaurantsPage from "./menu/RestaurantsPage";
import MenuPage from "./menu/MenuPage";
import {CartProvider} from "./CartContext";
import CartPage from "./menu/CartPage";
import OrderSummaryPage from "./orders/OrderSummaryPage";

const stopRuntimeLogging = installRuntimeLogging();
if (import.meta.hot) import.meta.hot.dispose(stopRuntimeLogging);

createRoot(document.getElementById("root")!, {
    onUncaughtError: (error) => logError(error, "Could not render the application."),
    onCaughtError: (error) => logError(error, "Could not render this page."),
}).render(
    <BrowserRouter>
        <AuthProvider>
            <CartProvider>
                <PageErrorBoundary>
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
                            <OrderSummaryPage />
                        }
                    />
                </Routes>
                </PageErrorBoundary>
                <OutputConsole/>
            </CartProvider>
        </AuthProvider>
    </BrowserRouter>
);
