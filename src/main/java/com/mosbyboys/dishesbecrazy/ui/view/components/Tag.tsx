import type { ReactNode } from "react";
import "./Tag.css";

type TagProps = {
    // sage: fresh/success, mustard: popular, tomato: scarcity, navy: info, neutral: inactive
    tone?: "sage" | "mustard" | "tomato" | "navy" | "neutral";
    className?: string;
    children: ReactNode;
};

export default function Tag({ tone = "neutral", className = "", children }: TagProps) {
    return (
        <span className={`tag tag--${tone} caption ${className}`.trim()}>
            {children}
        </span>
    );
}
