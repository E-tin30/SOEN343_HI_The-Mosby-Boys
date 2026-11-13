import plateLogo from "../assets/plate-logo.png";
import plateLogoTransparent from "../assets/plate-logo-transparent.png";
import "./PlateLogo.css";

type PlateLogoProps = {
    className?: string;
    transparent?: boolean;
};

export default function PlateLogo({ className = "", transparent = false }: PlateLogoProps) {
    return (
        <img
            src={transparent ? plateLogoTransparent : plateLogo}
            className={`plate-logo ${className}`.trim()}
            alt=""
            aria-hidden="true"
        />
    );
}
