import { useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";


const IMAGE_WIDTH = 1375;
const IMAGE_HEIGHT = 768;
// Taco bounding box in the image's own pixels.
const TACO = { x: 420, y: 228, width: 142, height: 112 };

export default function ClickableHotspot() {
    const navigate = useNavigate();
    const buttonRef = useRef<HTMLButtonElement>(null);
    const [style, setStyle] = useState<CSSProperties>({ display: "none" });

    useLayoutEffect(() => {
        const container = buttonRef.current?.parentElement;
        if (!container) return;

        const update = () => {
            const width = container.clientWidth;
            const height = container.clientHeight;
            const scale = Math.max(width / IMAGE_WIDTH, height / IMAGE_HEIGHT);
            const offsetX = width - IMAGE_WIDTH * scale; // right-aligned
            const offsetY = (height - IMAGE_HEIGHT * scale) / 2; // vertically centred

            const left = offsetX + TACO.x * scale;
            const top = offsetY + TACO.y * scale;
            const tacoWidth = TACO.width * scale;
            const tacoHeight = TACO.height * scale;

            // Hide it when the taco is cropped off-screen on narrow windows.
            const visible = left + tacoWidth > 0 && top + tacoHeight > 0 && left < width && top < height;
            setStyle(visible ? { left, top, width: tacoWidth, height: tacoHeight } : { display: "none" });
        };

        update();
        const observer = new ResizeObserver(update);
        observer.observe(container);
        return () => observer.disconnect();
    }, []);

    return (
        <button
            ref={buttonRef}
            type="button"
            className="clickable-hotspot"
            style={style}
            aria-label="Taco"
            tabIndex={-1}
            onClick={() => navigate("/taco")}
        />
    );
}
