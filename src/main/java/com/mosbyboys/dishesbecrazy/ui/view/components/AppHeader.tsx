import type { ReactNode } from "react";
import PlateLogo from "./PlateLogo";
import TextLogo from "./TextLogo";
import "./AppHeader.css";

type AppHeaderProps = {
    // Actions shown on the right, e.g. a Log out button.
    children?: ReactNode;
};

export default function AppHeader({ children }: AppHeaderProps) {
    return (
        <header className="app-header">
            <div className="app-header__brand">
                <PlateLogo />
                <TextLogo />
            </div>
            {children && <div className="app-header__actions">{children}</div>}
        </header>
    );
}
