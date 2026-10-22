import { createRoot } from "react-dom/client";
import "./styles/base.css";
import { AuthProvider } from "./AuthContext";
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
import RestaurantsPage from "./menu/RestaurantsPage";
import MenuPage from "./menu/MenuPage";

createRoot(document.getElementById("root")!).render(
  <BrowserRouter>
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegistrationPage />} />

        <Route
          path="/home"
          element={
            <ProtectedRoute>
              <HomePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/restaurants"
          element={
            <ProtectedRoute>
              <RestaurantsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/restaurants/:restaurantId/menu"
          element={
            <ProtectedRoute>
              <MenuPage />
            </ProtectedRoute>
          }
        />

      </Routes>
    </AuthProvider>
  </BrowserRouter>
);
