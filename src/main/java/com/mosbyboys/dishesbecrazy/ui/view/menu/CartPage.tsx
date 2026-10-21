import {Link} from "react-router-dom";
import {useCart} from "../CartContext";
import {useAuth} from "../AuthContext";
import {useState} from "react";

export default function CartPage() {
    const {
        cart,
        itemCount,
        subtotal,
        updateQuantity,
        removeItem,
        clearCart,
    } = useCart();
    const { accessToken, user } = useAuth();

    const [placingOrder, setPlacingOrder] = useState(false);
    const [orderError, setOrderError] = useState("");

    if (!cart || cart.items.length === 0) {
        return (
            <main>
                <h1>Your Cart</h1>
                <p>Your cart is empty.</p>
                <Link to="/restaurants">Browse Restaurants</Link>
            </main>
        );
    }

    return (
        <main>
            <h1>Your Cart</h1>

            <p>Total items: {itemCount}</p>

            <table border={1}>
                <thead>
                <tr>
                    <th>Dish</th>
                    <th>Price</th>
                    <th>Quantity</th>
                    <th>Total</th>
                    <th>Action</th>
                </tr>
                </thead>

                <tbody>
                {cart.items.map((item) => (
                    <tr key={item.menuItem.id}>
                        <td>{item.menuItem.name}</td>
                        <td>${item.menuItem.price.toFixed(2)}</td>

                        <td>
                            <button
                                type="button"
                                onClick={() =>
                                    updateQuantity(
                                        item.menuItem.id,
                                        item.quantity - 1
                                    )
                                }
                            >
                                -
                            </button>

                            {" "}{item.quantity}{" "}

                            <button
                                type="button"
                                onClick={() =>
                                    updateQuantity(
                                        item.menuItem.id,
                                        item.quantity + 1
                                    )
                                }
                            >
                                +
                            </button>
                        </td>

                        <td>
                            ${(item.menuItem.price * item.quantity).toFixed(2)}
                        </td>

                        <td>
                            <button
                                type="button"
                                onClick={() => removeItem(item.menuItem.id)}
                            >
                                Remove
                            </button>
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>

            <h2>Subtotal: ${subtotal.toFixed(2)}</h2>

            <button type="button" onClick={clearCart}>
                Clear Cart
            </button>

            <button
                type="button"
                disabled={placingOrder || !cart || cart.items.length === 0}
                onClick={async () => {
                    if (!cart || !accessToken) return;

                    setPlacingOrder(true);
                    setOrderError("");

                    try {
                        const response = await fetch(
                            "http://localhost:8080/placeOrder",
                            {
                                method: "POST",
                                headers: {
                                    "Content-Type": "application/json",
                                    Authorization: `Bearer ${accessToken}`,
                                },
                                body: JSON.stringify({
                                    user: user,
                                    userId: user?.id,
                                    restaurantId: cart.restaurantId,
                                    items: cart.items.map((item) => ({
                                        menuItemId: item.menuItem.id,
                                        quantity: item.quantity,
                                        price: item.menuItem.price,
                                    })),
                                }),
                            }
                        );

                        if (!response.ok) {
                            throw new Error(await response.text() || "Failed to place order.");
                        }

                        clearCart();
                        alert("Order placed successfully!");
                    } catch (error) {
                        setOrderError(
                            error instanceof Error ? error.message : "Something went wrong."
                        );
                    } finally {
                        setPlacingOrder(false);
                    }
                }}

            >
                Pay and Place Order
            </button>

            <p>
                <Link to={`/restaurants/${cart.restaurantId}/menu`}>
                    Back to Menu
                </Link>
            </p>
        </main>
    );
}