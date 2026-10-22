import "./TextLogo.css";

type TextLogoProps = {
    layout?: "horizontal" | "stacked";
    variant?: "color" | "mono" | "reversed";
    className?: string;
};

export default function TextLogo({ layout = "horizontal", variant = "color", className = "" }: TextLogoProps) {
    return (
        <span
            className={`text-logo text-logo--${layout} text-logo--${variant} ${className}`.trim()}
            role="img"
            aria-label="Dishes Be Crazy"
        >
            <span>Dishes Be</span>
            <span className="text-logo__crazy">Crazy.</span>
        </span>
    );
}
