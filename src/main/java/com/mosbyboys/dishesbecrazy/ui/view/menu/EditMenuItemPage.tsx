import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import {useNavigate} from "react-router-dom";

export default function EditMenuItemPage() {
    const navigate = useNavigate();

    return (
        <div className="page">
            <AppHeader>
                <Button variant="secondary" onClick={() => navigate("/home")}>
                    Back to home
                </Button>
            </AppHeader>

            <main className="page__content card">
                <header className="page__intro">
                    <Heading>Edit an ~item~ from your menu!</Heading>
                    <p className="meta">Select the menu item that you want to edit.</p>
                </header>



            </main>
        </div>
    );
}