package clean.code.successive.refinement.work.in.progress;

import java.text.ParseException;
import java.util.*;

import clean.code.successive.refinement.first.draft.ArgsException;

public class Args {

	private String schema;
	private String[] args;
	private boolean valid = true;
	private Set<Character> unexpectedArguments = new TreeSet<Character>();
	private Map<Character, ArgumentMarshaler> marshalers = new HashMap<Character, ArgumentMarshaler>();
	private Set<Character> argsFound = new HashSet<Character>();
	private int currentArgument;
	private char errorArgument = '\0';

	enum ErrorCode {
		OK, MISSING_STRING, MISSING_INTEGER, INVALID_INTEGER, MISSING_DOUBLE, INVALID_DOUBLE
	}

	private ErrorCode errorCode = ErrorCode.OK;

	public Args(String schema, String[] args) throws ParseException {
		this.schema = schema;
		this.args = args;
		valid = parse();
	}

	private boolean parse() throws ParseException {
		if (schema.length() == 0 && args.length == 0)
			return true;
		parseSchema();
		try {
			parseArguments();
		} catch (ArgsException e) {
			
			
		}
		return valid;
	}

	private boolean parseSchema() throws ParseException {
		for (String element : schema.split(",")) {
			if (element.length() > 0) {
				String trimmedElement = element.trim();
				parseSchemaElement(trimmedElement);
			}
		}
		return true;
	}

	private void parseSchemaElement(String element) throws ParseException {
		char elementId = element.charAt(0);
		String elementTail = element.substring(1);
		validateSchemaElementId(elementId);
		if (isBooleanSchemaElement(elementTail))
			marshalers.put(elementId, new BooleanArgumentMarshaler());
		else if (isStringSchemaElement(elementTail))
			marshalers.put(elementId, new StringArgumentMarshaler());
		else if (isIntSchemaElement(elementTail))
			marshalers.put(elementId, new IntegerArgumentMarshaler());
	}

	private void validateSchemaElementId(char elementId) throws ParseException {
		if (!Character.isLetter(elementId)) {
			throw new ParseException("Bad character:" + elementId
					+ "in Args format: " + schema, 0);
		}
	}

	private boolean isStringSchemaElement(String elementTail) {
		return elementTail.equals("*");
	}

	private boolean isBooleanSchemaElement(String elementTail) {
		return elementTail.length() == 0;
	}

	private boolean isIntSchemaElement(String elementTail) {
		return elementTail.equals("#");
	}

	private boolean parseArguments() throws ArgsException {
		for (currentArgument = 0; currentArgument < args.length; currentArgument++) {
			String arg = args[currentArgument];
			parseArgument(arg);
		}
		return true;
	}

	private void parseArgument(String arg) throws ArgsException {
		if (arg.startsWith("-"))
			parseElements(arg);
	}

	private void parseElements(String arg) throws ArgsException {
		for (int i = 1; i < arg.length(); i++)
			parseElement(arg.charAt(i));
	}

	private void parseElement(char argChar) throws ArgsException {
		if (setArgument(argChar))
			argsFound.add(argChar);
		else {
			unexpectedArguments.add(argChar);
			valid = false;
		}
	}

	private boolean setArgument(char argChar) throws ArgsException {
		ArgumentMarshaler m = marshalers.get(argChar);
		try {
			if (m instanceof BooleanArgumentMarshaler)
				setBooleanArg(m);
			else if (m instanceof StringArgumentMarshaler)
				setStringArg(m);
			else if (m instanceof IntegerArgumentMarshaler) {
				setIntArg(m);
			} else
				return false;
		} catch (ArgsException e) {
			valid = false;
			errorArgument = argChar;
			throw e;
		}
		return true;
	}

	private void setBooleanArg(ArgumentMarshaler m) {
		try {
			m.set("true");
		} catch (ArgsException e) {
		}
	}

	private void setStringArg(ArgumentMarshaler m) throws ArgsException {
		currentArgument++;
		try {
			m.set(args[currentArgument]);
		} catch (ArrayIndexOutOfBoundsException e) {
			errorCode = ErrorCode.MISSING_STRING;
			throw new ArgsException();
		}
	}

	private void setIntArg(ArgumentMarshaler m) throws ArgsException {
		currentArgument++;
		try {
			m.set(args[currentArgument]);
		} catch (ArrayIndexOutOfBoundsException e) {
			errorCode = ErrorCode.MISSING_INTEGER;
			throw new ArgsException();
		} catch (ArgsException e) {
			errorCode = ErrorCode.INVALID_INTEGER;
			throw e;
		}
	}

	public int cardinality() {
		return argsFound.size();
	}

	public String usage() {
		if (schema.length() > 0)
			return "-[" + schema + "]";
		else
			return "";
	}

	public String errorMessage() throws Exception {
		if (unexpectedArguments.size() > 0) {
			return unexpectedArgumentMessage();
		} else
			switch (errorCode) {
			case INVALID_INTEGER:
				return String.format(
						"Could not parse integer parameter for -%c.",
						errorArgument);
			case MISSING_INTEGER:
				return String.format(
						"Could not find integer parameter for -%c.",
						errorArgument);
			case MISSING_STRING:
				return String.format(
						"Could not find string parameter for -%c.",
						errorArgument);
			case OK:
				throw new Exception("TILT: Should not get here.");
			}
		return "";
	}

	private String unexpectedArgumentMessage() {
		StringBuffer message = new StringBuffer("Argument(s) -");
		for (char c : unexpectedArguments) {
			message.append(c);
		}
		message.append(" unexpected.");
		return message.toString();
	}

	public boolean getBoolean(char arg) {
		Args.ArgumentMarshaler am = marshalers.get(arg);
		boolean b = false;
		try {
			b = am != null && (Boolean) am.get();
		} catch (ClassCastException e) {
			b = false;
		}
		return b;
	}

	public String getString(char arg) {
		Args.ArgumentMarshaler am = marshalers.get(arg);
		try {
			return am != null ? (String) am.get() : "";
		} catch (ClassCastException e) {
			return "";
		}
	}

	public int getInteger(char arg) {
		Args.ArgumentMarshaler am = marshalers.get(arg);
		try {
			return am != null ? (Integer) am.get() : 0;
		} catch (Exception e) {
			return 0;
		}

	}

	public boolean has(char arg) {
		return argsFound.contains(arg);
	}

	public boolean isValid() {
		return valid;
	}

	private abstract class ArgumentMarshaler {

		public abstract Object get();

		public abstract void set(String s) throws ArgsException;

	}

	private class BooleanArgumentMarshaler extends ArgumentMarshaler {

		private boolean booleanValue;

		@Override
		public void set(String s) {
			booleanValue = true;
		}

		@Override
		public Object get() {
			return Boolean.TRUE.equals(booleanValue);
		}
	}

	private class StringArgumentMarshaler extends ArgumentMarshaler {

		private String stringValue;

		@Override
		public void set(String s) {
			stringValue = s;
		}

		@Override
		public Object get() {
			return stringValue != null ? stringValue : "";
		}
	}

	private class IntegerArgumentMarshaler extends ArgumentMarshaler {

		private int integerValue;

		@Override
		public void set(String s) throws ArgsException {
			try {
				integerValue = Integer.parseInt(s);
			} catch (NumberFormatException e) {
				throw new ArgsException();
			}
		}

		@Override
		public Object get() {
			return integerValue;
		}
	}

}
