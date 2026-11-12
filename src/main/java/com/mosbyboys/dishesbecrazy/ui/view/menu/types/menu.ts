export type Restaurant = {
  id: string;
  name: string;
  status: "OPEN" | "PAUSED" | "CLOSED";
};

export type MenuItem = {
  id: string;
  name: string;
  price: number;
  stockQuantity: number;
  status: "AVAILABLE" | "SOLD_OUT";
  description: string | null;
};
