from dataclasses import dataclass, asdict

@dataclass
class User:
    id: int
    username: str
    email: str
    password: str

    def __post_init__(self):
        if isinstance(self.id, str):
            self.id = int(self.id)

@dataclass
class Product:
    id: int
    name: str
    description: str
    price: float
    quantity: int

    def __post_init__(self):

        if isinstance(self.id, str):
            self.id = int(self.id)

        if isinstance(self.price, str):
            self.price = float(self.price)

        if isinstance(self.quantity, str):
            self.quantity = int(self.quantity)


@dataclass
class UserServiceRequest:
    endpoint: str
    headers: dict
    method: str
    payload: str
