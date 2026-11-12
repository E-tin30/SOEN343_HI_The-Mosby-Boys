import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { useEffect, useState, type FormEvent, type ChangeEvent } from "react";
import type { MenuItem } from "./types/menu";
import { getMenu } from "./api/menuApi";
import "../styles/EditMenuItemPage.css";

type EditFormData = {
    name: string;
    description: string;
    stockQuantity: string;
    price: string;
};

export default function EditMenuItemPage() {
    const navigate = useNavigate();
    const { accessToken, user } = useAuth();
    const restaurantId = user?.id

    const [items, setItems] = useState<MenuItem[] | null>(null);
    const [error, setError] = useState("");
    const [editingId, setEditingId] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const [formData, setFormData] = useState<EditFormData>({
        name: "",
        description: "",
        stockQuantity: "",
        price: ""
    });

    useEffect(() => {
        if (!accessToken || !restaurantId) return;

        getMenu(accessToken, restaurantId)
            .then(setItems)
            .catch((e: Error) => setError(e.message));
    }, [accessToken, restaurantId]);

    const handleEdit = (item: MenuItem) => {
        setError("");

        setEditingId(item.id);

        setFormData({
            name: item.name,
            description: item.description ?? "",
            stockQuantity: String(item.stockQuantity),
            price: String(item.price)
        });
    };

    const handleCancel = () => {
        setEditingId(null);
        setError("");

        setFormData({
            name: "",
            description: "",
            stockQuantity: "",
            price: ""
        });
    };

    const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;

        setFormData(prev => ({
            ...prev,
            [name]: value
        }));
    };

    const handleSubmit = async (
        e: FormEvent<HTMLFormElement>,
        itemId: string
    ) => {
        e.preventDefault();
        setError("");

        if (
            !formData.name.trim() ||
            formData.price === "" ||
            formData.stockQuantity === ""
        ) {
            setError("Please fill out all required fields.");
            return;
        }

        if (
            !Number.isFinite(Number(formData.price)) ||
            !Number.isFinite(Number(formData.stockQuantity)) ||
            Number(formData.price) <= 0 ||
            Number(formData.stockQuantity) < 0
        ) {
            setError("Please enter a valid price and stock quantity.");
            return;
        }

        const updatedItem = {
            id: itemId,
            name: formData.name.trim(),
            price: Number(formData.price),
            stockQuantity: Number(formData.stockQuantity),
            description: formData.description.trim()
        };

        try {
            setLoading(true);

            const response = await fetch("http://localhost:8080/menu/edit", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${accessToken}`
                },
                body: JSON.stringify(updatedItem)
            });

            if (!response.ok) {
                throw new Error("Failed to update menu item.");
            }

            // Update the displayed item without reloading the page.
            setItems(prev =>
                prev?.map(item =>
                    item.id === itemId
                        ? { ...item, ...updatedItem }
                        : item
                ) ?? null
            );

            setEditingId(null);

            setFormData({
                name: "",
                description: "",
                stockQuantity: "",
                price: ""
            });

            alert("Menu item updated successfully!");

        } catch (err: unknown) {
            setError(
                err instanceof Error
                    ? err.message
                    : "An unexpected error occurred."
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="page">
            <AppHeader>
                <Button
                    variant="secondary"
                    onClick={() => navigate("/home")}
                >
                    Back to home
                </Button>
            </AppHeader>

            <main className="page__content card">
                <header className="page__intro">
                    <Heading>Edit an ~item~ from your menu!</Heading>
                    <p className="meta">
                        Click the 'Edit' button to edit the menu item.
                    </p>
                </header>

                {error && (
                    <p
                        className="page__status page__status--error"
                        role="alert"
                    >
                        Error: {error}
                    </p>
                )}

                {!error && items === null && (
                    <p className="page__status meta">Loading…</p>
                )}

                {items?.length === 0 && (
                    <p className="page__status meta">
                        This restaurant has no menu items yet.
                    </p>
                )}

                {items && items.length > 0 && (
                    <ul className="page__list">
                        {items.map(item => {
                            const isEditing = editingId === item.id;

                            return (
                                <li
                                    key={item.id}
                                    className={`list-card menu-item`}
                                >
                                    {isEditing ? (
                                        <form
                                            className="menu-item__edit-form"
                                            onSubmit={e =>
                                                handleSubmit(e, item.id)
                                            }
                                        >
                                            <div className="menu-item__edit-fields">
                                                <label>
                                                    Name
                                                    <input
                                                        type="text"
                                                        name="name"
                                                        value={formData.name}
                                                        onChange={handleChange}
                                                        required
                                                    />
                                                </label>

                                                <label>
                                                    Description
                                                    <textarea
                                                        name="description"
                                                        value={formData.description}
                                                        onChange={handleChange}
                                                        rows={3}
                                                    />
                                                </label>

                                                <label>
                                                    Stock quantity
                                                    <input
                                                        type="number"
                                                        name="stockQuantity"
                                                        min="0"
                                                        step="1"
                                                        value={formData.stockQuantity}
                                                        onChange={handleChange}
                                                        required
                                                    />
                                                </label>

                                                <label>
                                                    Price ($)
                                                    <input
                                                        type="number"
                                                        name="price"
                                                        min="0.01"
                                                        step="0.01"
                                                        value={formData.price}
                                                        onChange={handleChange}
                                                        required
                                                    />
                                                </label>
                                            </div>

                                            <div className="menu-item__actions">
                                                <Button
                                                    type="submit"
                                                    disabled={loading}
                                                >
                                                    {loading ? "Saving..." : "Confirm"}
                                                </Button>

                                                <Button
                                                    type="button"
                                                    variant="secondary"
                                                    onClick={handleCancel}
                                                    disabled={loading}
                                                >
                                                    Cancel
                                                </Button>
                                            </div>
                                        </form>
                                    ) : (
                                        <>
                                            <div className="list-card__info">
                                                <Heading level={3}>
                                                    {item.name}
                                                </Heading>

                                                {item.description && (
                                                    <p className="menu-item__description meta">
                                                        {item.description}
                                                    </p>
                                                )}

                                                <p className="meta">
                                                    Stock: {item.stockQuantity}
                                                </p>
                                            </div>

                                            <div className="list-card__side">
                                                <Button
                                                    type="button"
                                                    variant="secondary"
                                                    onClick={() => handleEdit(item)}
                                                    disabled={editingId !== null}
                                                >
                                                    edit
                                                </Button>

                                                <span className="list-card__price">
                                                    ${item.price.toFixed(2)}
                                                </span>
                                            </div>
                                        </>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                )}
            </main>
        </div>
    );
}
