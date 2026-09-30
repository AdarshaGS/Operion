import { useEffect, useState } from "react";
import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { listPersons, type PersonResponse } from "../api/persons";

interface PersonPickerProps {
	value: number | "";
	onChange: (personId: number | "") => void;
	label?: string;
	required?: boolean;
	fullWidth?: boolean;
}

function personLabel(person: PersonResponse): string {
	return `${person.firstName}${person.lastName ? " " + person.lastName : ""}${person.email ? " (" + person.email + ")" : ""}`;
}

/** Picks any Person - student, guardian or staff - for privacy records, which attach to the
 * person rather than to a role they hold. Fetches once on mount, so a dialog that mounts it
 * only while open (the usual pattern here) always sees people added since the last visit. */
export function PersonPicker({ value, onChange, label = "Person", required, fullWidth }: PersonPickerProps) {
	const [options, setOptions] = useState<PersonResponse[]>([]);

	useEffect(() => {
		listPersons().then(setOptions).catch(() => setOptions([]));
	}, []);

	return (
		<Autocomplete
			options={options}
			value={options.find((person) => person.id === value) ?? null}
			getOptionLabel={personLabel}
			isOptionEqualToValue={(a, b) => a.id === b.id}
			onChange={(_event, person) => onChange(person ? person.id : "")}
			fullWidth={fullWidth}
			renderInput={(params) => <TextField {...params} label={label} required={required} />}
		/>
	);
}
