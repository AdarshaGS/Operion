import { useEffect, useState } from "react";
import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { searchStudents, type StudentListRowResponse } from "../api/students";

interface StudentPickerProps {
	value: number | "";
	onChange: (studentId: number | "", student: StudentListRowResponse | null) => void;
	label?: string;
	required?: boolean;
	fullWidth?: boolean;
}

function studentLabel(student: StudentListRowResponse): string {
	return `${student.firstName}${student.lastName ? " " + student.lastName : ""} (${student.studentId})`;
}

/** Reusable server-searched student picker (debounced, 300ms) - schools run 500-2,000+
 * students, so unlike CustomerPicker/department pickers this can't just fetch everything
 * up front. Renders name + system-generated student id to disambiguate common names. */
export function StudentPicker({ value, onChange, label = "Student", required, fullWidth }: StudentPickerProps) {
	const [inputValue, setInputValue] = useState("");
	const [options, setOptions] = useState<StudentListRowResponse[]>([]);
	const [selected, setSelected] = useState<StudentListRowResponse | null>(null);

	useEffect(() => {
		const handle = setTimeout(() => {
			searchStudents({ search: inputValue || null, size: 20 })
				.then((page) => setOptions(page.content))
				.catch(() => setOptions([]));
		}, 300);
		return () => clearTimeout(handle);
	}, [inputValue]);

	useEffect(() => {
		if (value === "") setSelected(null);
	}, [value]);

	return (
		<Autocomplete
			options={options}
			value={selected}
			onChange={(_event, student) => {
				setSelected(student);
				onChange(student ? student.id : "", student);
			}}
			inputValue={inputValue}
			onInputChange={(_event, newInputValue) => setInputValue(newInputValue)}
			getOptionLabel={(student) => studentLabel(student)}
			isOptionEqualToValue={(option, val) => option.id === val.id}
			renderInput={(params) => <TextField {...params} label={label} required={required} />}
			fullWidth={fullWidth}
		/>
	);
}
