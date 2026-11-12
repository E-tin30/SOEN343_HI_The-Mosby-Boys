import {ChangeEvent, FormEvent, useState} from "react";
import {useNavigate} from "react-router-dom";

import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import {useAuth} from "../AuthContext";
import Input from "../components/Input";
import {loggedFetch} from "../api/loggedFetch";

export default function AddMenuItemPage() {
    const navigate = useNavigate();
    const {accessToken} = useAuth();

    const [formData, setFormData] = useState({
        name: "",
        price: "",
        stockQuantity: "",
        description: "",
        //imageUrl: ""
    });

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
        const {name, value} = e.target;

        setFormData((previous) => ({
            ...previous,
            [name]: value
        }));
    };

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
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
            Number(formData.price) <= 0 ||
            Number(formData.stockQuantity) < 0
        ) {
            setError("Please enter a valid price and stock quantity.");
            return;
        }

        const item = {
            name: formData.name.trim(),
            price: Number(formData.price),
            stockQuantity: Number(formData.stockQuantity),
            description: formData.description.trim(),
            //imageUrl: formData.imageUrl.trim()
        };

        try {
            setLoading(true);

            const response = await loggedFetch("http://localhost:8080/menu/add", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${accessToken}`
                },
                body: JSON.stringify(item)
            });

            if (!response.ok) {
                throw new Error("Failed to add menu item.");
            }

            navigate("/home");
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
                    <Heading>Add an ~item~ to your menu!</Heading>
                    <p className="meta">
                        Fill out the required fields and click the
                        'add item' button.
                    </p>
                </header>
            </main>
            <section>
                <form className="page__content card margin-top" onSubmit={handleSubmit}>
                    <div className="login-card__fields">
                        <Input
                            label="Item name"
                            name="name"
                            placeholder="e.g. Classic Cheeseburger"
                            value={formData.name}
                            onChange={handleChange}
                            required
                        />

                        <Input
                            label="Price ($)"
                            type="number"
                            name="price"
                            placeholder="0.00"
                            min="0.01"
                            step="0.01"
                            value={formData.price}
                            onChange={handleChange}
                            required
                        />

                        <Input
                            label="Stock quantity"
                            type="number"
                            name="stockQuantity"
                            placeholder="0"
                            min="0"
                            step="1"
                            value={formData.stockQuantity}
                            onChange={handleChange}
                            required
                        />

                        <Input
                            label="Description"
                            name="description"
                            placeholder="Describe your menu item..."
                            value={formData.description}
                            onChange={handleChange}
                            required
                        />

                        {/*<Input
                            label="Image URL"
                            type="url"
                            name="imageUrl"
                            placeholder="https://example.com/image.jpg"
                            value={formData.imageUrl}
                            onChange={handleChange}
                        />*/}
                    </div>

                    {error && (
                        <p className="login-card__error" role="alert">
                            {error}
                        </p>
                    )}

                    <Button type="submit" block disabled={loading}>
                        {loading ? "Adding item..." : "Add item"}
                    </Button>

                    <p className="login-card__footer meta">
                        Changed your mind?{" "}
                        <Button
                            variant="link"
                            onClick={() => navigate("/home")}
                        >
                            Go back home
                        </Button>
                    </p>
                </form>
            </section>
        </div>
    );
}
