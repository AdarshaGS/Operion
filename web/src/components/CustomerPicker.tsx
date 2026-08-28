import { useEffect, useState } from "react";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import { listCustomers, type CustomerResponse } from "../api/customers";

interface CustomerPickerProps {
	value: number | "";
	onChange: (customerId: number | "") => void;
	label?: string;
	required?: boolean;
	fullWidth?: boolean;
}

/** Reusable across any screen that needs to attribute a transaction to a customer (the
 * future Sales module's checkout screen, per GitHub #52) - lists active customers only,
 * same convention as the role/campus/department pickers elsewhere. Managing customers
 * themselves (creating a walk-in, linking a student) happens on CustomersPanel, not here. */
export function CustomerPicker({ value, onChange, label = "Customer", required, fullWidth }: CustomerPickerProps) {
	const [customers, setCustomers] = useState<CustomerResponse[]>([]);

	useEffect(() => {
		listCustomers()
			.then((all) => setCustomers(all.filter((customer) => customer.status === "ACTIVE")))
			.catch(() => {});
	}, []);

	return (
		<TextField
			select
			label={label}
			value={value}
			onChange={(e) => onChange(e.target.value === "" ? "" : Number(e.target.value))}
			required={required}
			fullWidth={fullWidth}
		>
			{customers.map((customer) => (
				<MenuItem key={customer.id} value={customer.id}>
					{customer.name}
					{customer.phone ? ` (${customer.phone})` : ""}
				</MenuItem>
			))}
		</TextField>
	);
}
