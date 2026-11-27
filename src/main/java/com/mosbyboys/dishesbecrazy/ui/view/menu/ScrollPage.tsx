import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {useAuth} from "../context/AuthContext";
import {useCart} from "../context/CartContext";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import Tag from "../components/Tag";
import {getOpenRestaurantMenu} from "./api/menuApi";
import type {MenuItem} from "./types/menu";
import "../styles/ScrollPage.css";

function shuffleItems<T>(items: T[]): T[] {
    const shuffled = [...items];

    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }

    return shuffled;
}

export default function ScrollPage() {
    const navigate = useNavigate();

    const {accessToken, user} = useAuth();
    const {addItem} = useCart();

    const [items, setItems] = useState<MenuItem[] | null>(null);
    const [error, setError] = useState("");
    const [showSoldOut, setShowSoldOut] = useState(true);

    const isCustomer = user?.role === "CUSTOMER";

    useEffect(() => {
        if (!accessToken) return;

        getOpenRestaurantMenu(accessToken)
            .then((result) => {
                setItems(shuffleItems(result));
            })
            .catch((e: Error) => setError(e.message));
    }, [accessToken]);

    // Only display menu items that have been posted (ie has a description)
    const posts = items?.filter(
        (item) =>
            item.postDescription?.trim() &&
            (showSoldOut || item.status !== "SOLD_OUT")
    ) ?? [];

    const handlePostClick = (item: MenuItem) => {
        if (item.status === "SOLD_OUT" || !isCustomer) {
            return;
        }

        addItem(item.restaurantId, item);
        navigate("/cart", { state: { isFromFeed: true } });
    };

    return (
        <div className="page scroll-page">
            <AppHeader>
                <Button
                    variant="secondary"
                    onClick={() => navigate("/home")}
                >
                    Back Home
                </Button>
            </AppHeader>

            <main className="scroll-page__content">
                <header className="scroll-page__intro">
                    <Heading>Discover what's cooking.</Heading>
                    <p className="meta">
                        Scroll through your community's latest creations.
                    </p>
                    <label className="scroll-page__filter">
                        <input
                            type="checkbox"
                            checked={showSoldOut}
                            onChange={(e) => setShowSoldOut(e.target.checked)}
                        />
                        Show sold out
                    </label>
                </header>

                {error && (
                    <p className="page__status page__status--error" role="alert">
                        Error: {error}
                    </p>
                )}

                {!error && items === null && (
                    <p className="page__status meta">Loading posts...</p>
                )}

                {items !== null && posts.length === 0 && (
                    <p className="page__status meta">
                        No food posts yet. Check back later!
                    </p>
                )}

                {posts.length > 0 && (
                    <div className="scroll-feed">
                        {posts.map((item) => {
                            const soldOut = item.status === "SOLD_OUT";

                            return (
                                <article
                                    key={item.id}
                                    className={`scroll-post${soldOut ? " scroll-post--sold-out" : ""}`}
                                    role="button"
                                    tabIndex={soldOut || !isCustomer ? -1 : 0}
                                    aria-label={`Add ${item.name} to cart`}
                                    aria-disabled={soldOut || !isCustomer}
                                    onClick={() => handlePostClick(item)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" || e.key === " ") {
                                            e.preventDefault();
                                            handlePostClick(item);
                                        }
                                    }}
                                >
                                    {/* Post header */}
                                    <div className="scroll-post__header">
                                        <div className="scroll-post__avatar">
                                            {(item.restaurantName ?? item.name).charAt(0).toUpperCase()}
                                        </div>

                                        <div className="scroll-post__title">
                                            <Heading level={3}>{item.restaurantName ?? item.name}</Heading>
                                        </div>

                                        {soldOut && <Tag>Sold out</Tag>}
                                    </div>

                                    {/* Post image */}
                                    <div className="scroll-post__image-container">
                                        {item.imageUrl ? (
                                            <img
                                                src={item.imageUrl}
                                                alt={item.name}
                                                className="scroll-post__image"
                                                loading="lazy"
                                            />
                                        ) : (
                                            <div className="scroll-post__image-placeholder">
                                                No image available
                                            </div>
                                        )}

                                        {!soldOut && isCustomer && (
                                            <div className="scroll-post__overlay">
                                                <span>Tap to add to cart</span>
                                            </div>
                                        )}

                                        {soldOut && (
                                            <div className="scroll-post__sold-out-overlay">
                                                SOLD OUT
                                            </div>
                                        )}
                                    </div>

                                    {/* Post description */}
                                    <div className="scroll-post__body">
                                        <div className="scroll-post__item-name">{item.name}</div>
                                        <div className="scroll-post__actions">
                                          <span className="scroll-post__price">
                                            ${item.price.toFixed(2)}
                                          </span>
                                            {isCustomer && !soldOut && (
                                                <span className="scroll-post__add">
                                                  + Add to cart
                                                </span>
                                            )}
                                        </div>

                                        {item.postDescription && (
                                            <p className="scroll-post__description">
                                                {item.postDescription}
                                            </p>
                                        )}
                                    </div>
                                </article>
                            );
                        })}
                    </div>
                )}
            </main>
        </div>
    );
}
