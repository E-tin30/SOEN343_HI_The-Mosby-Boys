import { useId, type InputHTMLAttributes } from "react";
import "./Input.css";

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
    label: string;
};

export default function Input({ label, id, className = "", ...props }: InputProps) {
    const generatedId = useId();
    const inputId = id ?? generatedId;

    return (
        <div className="field">
            <label htmlFor={inputId} className="field__label caption">
                {label}
            </label>
            <input id={inputId} className={`input ${className}`.trim()} {...props} />
        </div>
    );
}
