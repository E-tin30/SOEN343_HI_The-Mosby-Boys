import plateLogo from "../assets/plate-logo.png";
import "./PlateLogo.css";

type PlateLogoProps = {
    className?: string;
};

export default function PlateLogo({ className = "" }: PlateLogoProps) {
    return (
        <img
            src={plateLogo}
            className={`plate-logo ${className}`.trim()}
            alt=""
            aria-hidden="true"
        />
    );
}
