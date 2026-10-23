import type { MenuItem } from "./menu";

export interface CartItem {
    menuItem: MenuItem;
    quantity: number;
}

export interface Cart {
    restaurantId: string;
    items: CartItem[];
}