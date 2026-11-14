import { useCallback, useEffect, useRef, useState } from "react";

// How long the panel stays open after the pointer leaves the marker, so it can move onto the panel
const CLOSE_DELAY_MS = 200;

// Opens a details panel while a marker (or the panel itself) is hovered
// Spread markerProps(item) on each marker and panelProps on the panel
export function useHoverPanel<T>() {
    const [selected, setSelected] = useState<T | null>(null);
    const closeTimer = useRef<number | undefined>(undefined);

    const cancelClose = useCallback(() => window.clearTimeout(closeTimer.current), []);

    const open = useCallback((item: T) => {
        cancelClose();
        setSelected(item);
    }, [cancelClose]);

    const scheduleClose = useCallback(() => {
        cancelClose();
        closeTimer.current = window.setTimeout(() => setSelected(null), CLOSE_DELAY_MS);
    }, [cancelClose]);

    const close = useCallback(() => {
        cancelClose();
        setSelected(null);
    }, [cancelClose]);

    useEffect(() => cancelClose, [cancelClose]);

    return {
        selected,
        close,
        // onClick and onFocus open it too, for touch screens and keyboard users.
        markerProps: (item: T) => ({
            onMouseEnter: () => open(item),
            onMouseLeave: scheduleClose,
            onFocus: () => open(item),
            onBlur: scheduleClose,
            onClick: () => open(item),
        }),
        panelProps: {
            onMouseEnter: cancelClose,
            onMouseLeave: scheduleClose,
        },
    };
}
