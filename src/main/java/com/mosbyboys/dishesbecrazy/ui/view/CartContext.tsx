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
        if (item.status === "SOLD_OUT") return;

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

    const clearCart = () => setCart(null);

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