
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useEffect, useState, type ChangeEvent } from "react";
import type { MenuItem } from "./types/menu";
import { getMenu } from "./api/menuApi";
import "../styles/EditMenuItemPage.css";

export default function PostMenuItemPage() {
    const navigate = useNavigate();
    const { accessToken, user } = useAuth();
    const restaurantId = user?.id;

    const [items, setItems] = useState<MenuItem[] | null>(null);
    const [descriptions, setDescriptions] = useState<Record<string, string>>({});
    const [error, setError] = useState("");
    const [updatingId, setUpdatingId] = useState<string | null>(null);

    useEffect(() => {
        if (!accessToken || !restaurantId) return;

        getMenu(accessToken, restaurantId)
            .then(menuItems => {
                setItems(menuItems);

                //Initialize descriptions for unposted items.
                setDescriptions(
                    Object.fromEntries(
                        menuItems.map(item => [
                            item.id,
                            item.postDescription ?? ""
                        ])
                    )
                );
            })
            .catch((e: Error) => setError(e.message));
    }, [accessToken, restaurantId]);

    const handleDescriptionChange = (
        e: ChangeEvent<HTMLTextAreaElement>,
        itemId: string
    ) => {
        const { value } = e.target;

        setDescriptions(prev => ({
            ...prev,
            [itemId]: value
        }));
    };

    const handleUpdatePosting = async (
        item: MenuItem,
        postDescription: string | null
    ) => {
        setError("");

        if (
            postDescription !== null &&
            !postDescription.trim()
        ) {
            setError("Please enter a description before posting this item.");
            return;
        }

        if (!accessToken) {
            setError("You must be logged in to update menu items.");
            return;
        }

        const updatedItem = {
            id: item.id,
            name: item.name,
            price: item.price,
            stockQuantity: item.stockQuantity,
            description: item.description,
            postDescription: postDescription?.trim() ?? null,
            imageUrl: item.imageUrl
        };

        try {
            setUpdatingId(item.id);

            const response = await fetch("http://localhost:8080/menu/edit", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${accessToken}`
                },
                body: JSON.stringify(updatedItem)
            });

            if (!response.ok) {
                if (response.status === 412) {
                    throw new Error(
                        "This menu item does not belong to this restaurant."
                    );
                }

                throw new Error("Failed to update menu item posting.");
            }

            // Update the displayed item without reloading the page.
            setItems(prev =>
                prev?.map(existingItem =>
                    existingItem.id === item.id
                        ? { ...existingItem, postDescription: updatedItem.postDescription }
                        : existingItem
                ) ?? null
            );

            // Clear the input when a posting is removed.
            setDescriptions(prev => ({
                ...prev,
                [item.id]: updatedItem.postDescription ?? ""
            }));

        } catch (err: unknown) {
            setError(
                err instanceof Error
                    ? err.message
                    : "An unexpected error occurred."
            );
        } finally {
            setUpdatingId(null);
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
                    <Heading>Manage your menu postings!</Heading>
                    <p className="meta">
                        Post menu items to advertise them to customers, or remove existing postings.
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
                            const isPosted = Boolean(
                                item.postDescription?.trim()
                            );

                            const isUpdating = updatingId === item.id;

                            return (
                                <li
                                    key={item.id}
                                    className="list-card menu-item"
                                >
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

                                        <p className="meta">
                                            Status:{" "}
                                            <strong>
                                                {isPosted ? "Posted" : "Not posted"}
                                            </strong>
                                        </p>

                                        {isPosted ? (
                                            <p className="menu-item__description meta">
                                                <i>
                                                    {item.postDescription}
                                                </i>
                                            </p>
                                        ) : (
                                            <label className="menu-item__edit-fields">
                                                Post description
                                                <textarea
                                                    value={descriptions[item.id] ?? ""}
                                                    onChange={e =>
                                                        handleDescriptionChange(
                                                            e,
                                                            item.id
                                                        )
                                                    }
                                                    rows={2}
                                                    placeholder="Write a description for your post..."
                                                    disabled={isUpdating}
                                                />
                                            </label>
                                        )}
                                    </div>

                                    <div className="list-card__side">
                                        <span className="list-card__price">
                                            ${item.price.toFixed(2)}
                                        </span>

                                        {isPosted ? (
                                            <Button
                                                type="button"
                                                variant="secondary"
                                                onClick={() =>
                                                    handleUpdatePosting(item, null)
                                                }
                                                disabled={updatingId !== null}
                                            >
                                                {isUpdating
                                                    ? "Removing..."
                                                    : "Remove posting"}
                                            </Button>
                                        ) : (
                                            <Button
                                                type="button"
                                                onClick={() =>
                                                    handleUpdatePosting(
                                                        item,
                                                        descriptions[item.id] ?? ""
                                                    )
                                                }
                                                disabled={
                                                    updatingId !== null ||
                                                    !descriptions[item.id]?.trim()
                                                }
                                            >
                                                {isUpdating
                                                    ? "Posting..."
                                                    : "Post item"}
                                            </Button>
                                        )}
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </main>
        </div>
    );
}
