import { ChangeEvent, FormEvent, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import { useAuth } from "../AuthContext";
import Input from "../components/Input";
import { loggedFetch } from "../api/loggedFetch";
import "../styles/ImportImages.css";

export default function AddMenuItemPage() {
    const navigate = useNavigate();
    const { accessToken } = useAuth();
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [formData, setFormData] = useState({
        name: "",
        price: "",
        stockQuantity: "",
        description: ""
    });

    const [image, setImage] = useState<File | null>(null);
    const [imagePreview, setImagePreview] = useState<string | null>(null);
    const [isDragging, setIsDragging] = useState(false);

    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleChange = (
        e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
    ) => {
        const { name, value } = e.target;

        setFormData(previous => ({
            ...previous,
            [name]: value
        }));
    };

    const handleImageChange = (file: File | undefined) => {
        if (!file) return;

        if (!file.type.startsWith("image/")) {
            setError("Please select a valid image.");
            return;
        }

        if (file.size > 5 * 1024 * 1024) {
            setError("Image must be smaller than 5 MB.");
            return;
        }

        setError("");
        setImage(file);
        setImagePreview(URL.createObjectURL(file));
    };

    const handleFileInput = (e: ChangeEvent<HTMLInputElement>) => {
        handleImageChange(e.target.files?.[0]);
    };

    const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        setIsDragging(false);

        handleImageChange(e.dataTransfer.files?.[0]);
    };

    const removeImage = () => {
        setImage(null);
        setImagePreview(null);

        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
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
            Number(formData.stockQuantity) < 0 ||
            !Number.isInteger(Number(formData.stockQuantity))
        ) {
            setError("Please enter a valid price and stock quantity.");
            return;
        }

        const data = new FormData();

        data.append("name", formData.name.trim());
        data.append("price", formData.price);
        data.append("stockQuantity", formData.stockQuantity);
        data.append("description", formData.description.trim());

        if (image) {
            data.append("image", image);
        }

        try {
            setLoading(true);

            const response = await loggedFetch(
                "http://localhost:8080/menu/add",
                {
                    method: "POST",
                    headers: {
                        Authorization: `Bearer ${accessToken}`
                    },
                    body: data
                }
            );

            if (!response.ok) {
                throw new Error("Failed to add menu item.");
            }

            alert("Menu item added successfully!");
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
                <form
                    className="page__content card margin-top"
                    onSubmit={handleSubmit}
                >
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

                        <div className="image-upload">
                            <label>Menu item image</label>

                            <div
                                className={`image-upload__dropzone ${
                                    isDragging ? "dragging" : ""
                                }`}
                                onDragOver={e => {
                                    e.preventDefault();
                                    setIsDragging(true);
                                }}
                                onDragLeave={() => setIsDragging(false)}
                                onDrop={handleDrop}
                                onClick={() => fileInputRef.current?.click()}
                            >
                                {imagePreview ? (
                                    <div className="image-upload__preview">
                                        <img
                                            src={imagePreview}
                                            alt="Menu item preview"
                                        />
                                        <p>{image?.name}</p>
                                    </div>
                                ) : (
                                    <div className="image-upload__placeholder">
                                        <span className="image-upload__icon">
                                            +
                                        </span>
                                        <p>
                                            <strong>
                                                Click to upload
                                            </strong>{" "}
                                            or drag and drop
                                        </p>
                                        <span className="meta">
                                            PNG, JPG or WEBP (max. 5 MB)
                                        </span>
                                    </div>
                                )}
                            </div>

                            <input
                                ref={fileInputRef}
                                type="file"
                                accept="image/png,image/jpeg,image/webp"
                                hidden
                                onChange={handleFileInput}
                            />

                            {image && (
                                <button
                                    type="button"
                                    className="image-upload__remove"
                                    onClick={removeImage}
                                >
                                    Remove image
                                </button>
                            )}
                        </div>
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
