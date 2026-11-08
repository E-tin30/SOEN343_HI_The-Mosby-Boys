import { log } from "./outputLog";
import {
    createContext,
    useContext,
    useState,
    type ReactNode,
} from "react";
import type { MenuItem } from "./menu/types/menu";
import type { Cart } from "./menu/types/cart";

interface CartContextType {
    cart: Cart | null;
    itemCount: number;
    subtotal: number;
    addItem: (restaurantId: string, item: MenuItem) => void;
    updateQuantity: (itemId: string, quantity: number) => void;
    removeItem: (itemId: string) => void;
    clearCart: () => void;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export function CartProvider({ children }: { children: ReactNode }) {
    const [cart, setCart] = useState<Cart | null>(null);

    const addItem = (restaurantId: string, item: MenuItem) => {
        log("command", "Add item to cart");
        // Records the local command outside React state updaters, which may be executed more than once.
        if (item.status === "SOLD_OUT") {
            log("error", "Cannot add a sold-out item.");
            // Makes the existing sold-out rejection visible in the output console.
            return;
        }
        if (cart && cart.restaurantId !== restaurantId) {
            log("error", "Cart items must come from one restaurant.");
            // Explains the existing single-restaurant cart rule when an add attempt is rejected.
            return;
        }

        setCart((current) => {
            // Start a new cart if there isn't one yet.
            if (!current) {
                return {
                    restaurantId,
                    items: [{ menuItem: item, quantity: 1 }],
                };
            }

            // Don't mix dishes from different restaurants.
            if (current.restaurantId !== restaurantId) {
                return current;
            }

            const existing = current.items.find(
                (cartItem) => cartItem.menuItem.id === item.id
            );

            if (existing) {
                return {
                    ...current,
                    items: current.items.map((cartItem) =>
                        cartItem.menuItem.id === item.id
                            ? { ...cartItem, quantity: cartItem.quantity + 1 }
                            : cartItem
                    ),
                };
            }

            return {
                ...current,
                items: [...current.items, { menuItem: item, quantity: 1 }],
            };
        });
    };

    const updateQuantity = (itemId: string, quantity: number) => {
        if (quantity <= 0) {
            removeItem(itemId);
            return;
        }
        log("command", "Update cart quantity");
        // Includes local quantity changes in R-DASH-06 command coverage.

        setCart((current) => {
            if (!current) return current;

            return {
                ...current,
                items: current.items.map((item) =>
                    item.menuItem.id === itemId ? { ...item, quantity } : item
                ),
            };
        });
    };

    const removeItem = (itemId: string) => {
        log("command", "Remove cart item");
        // Records removal commands even though they do not contact the backend.
        setCart((current) => {
            if (!current) return current;

            const remainingItems = current.items.filter(
                (item) => item.menuItem.id !== itemId
            );

            return remainingItems.length > 0
                ? { ...current, items: remainingItems }
                : null;
        });
    };

    const clearCart = () => {
        log("command", "Clear cart");
        setCart(null);
    };
    // Records clearing the cart and preserves the existing local reset behavior.

    const itemCount =
        cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0;

    const subtotal =
        cart?.items.reduce(
            (total, item) => total + item.menuItem.price * item.quantity,
            0
        ) ?? 0;

    return (
        <CartContext.Provider
            value={{
                cart,
                itemCount,
                subtotal,
                addItem,
                updateQuantity,
                removeItem,
                clearCart,
            }}
        >
            {children}
        </CartContext.Provider>
    );
}

export function useCart() {
    const context = useContext(CartContext);

    if (!context) {
        throw new Error("useCart must be used within a CartProvider");
    }

    return context;
}