import type { ButtonHTMLAttributes } from "react";
import "./Button.css";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: "primary" | "secondary" | "link";
    block?: boolean;
};

export default function Button({
    variant = "primary",
    block = false,
    className = "",
    type = "button",
    ...props
}: ButtonProps) {
    const classes = ["btn", `btn--${variant}`, block && "btn--block", className]
        .filter(Boolean)
        .join(" ");

    return <button type={type} className={classes} {...props} />;
}
