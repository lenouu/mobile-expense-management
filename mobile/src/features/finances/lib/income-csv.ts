import { detectDelimiter, findField, splitLine } from '@/features/finances/lib/csv';

/** One row of an income CSV, already checked against the account's own sources. */
export type IncomeCsvRow = {
  /** The line in the file, 2-based because the header is line 1. */
  line: number;
  date: string;
  amount: number;
  sourceName: string;
  sourceId: string;
  name: string;
  description: string;
};

export type IncomeCsvProblem = { line: number; message: string };

export type IncomeCsvResult = {
  rows: IncomeCsvRow[];
  problems: IncomeCsvProblem[];
  /** Problems with the header itself: with these, no row can be read. */
  headerProblems: string[];
};

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const AMOUNT = /^\d+(\.\d{1,2})?$/;

const ALIASES = {
  date: ['date', 'when', 'day'],
  amount: ['amount', 'total', 'value', 'sum'],
  source: ['source', 'from', 'income source'],
  name: ['name', 'label', 'title'],
  description: ['description', 'note', 'notes', 'details'],
};

function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Reads an income CSV: `date, amount, source, name, description`.
 *
 * <p>The mirror of {@link parseExpensesCsv}, sharing its tokeniser so both files are read the same
 * way - quoted cells, either delimiter, a byte-order mark, CRLF or LF. The difference is what a row
 * has to name: an income names a **source** the account owns, and may carry a label.
 *
 * <p>A row that names a source the user does not have is a problem rather than a silent import,
 * because guessing would put money in the wrong bucket.
 */
export function parseIncomesCsv(
  text: string,
  sources: { id: string; name: string }[],
  today: string = todayIso(),
): IncomeCsvResult {
  const lines = text
    .replace(/^\uFEFF/, '')
    .split(/\r\n|\n|\r/)
    .filter((line) => line.trim().length > 0);

  if (lines.length === 0) {
    return { rows: [], problems: [], headerProblems: ['The file is empty.'] };
  }

  const delimiter = detectDelimiter(lines[0]);
  const header = splitLine(lines[0], delimiter);

  const columns = {
    date: findField(header, ALIASES.date),
    amount: findField(header, ALIASES.amount),
    source: findField(header, ALIASES.source),
    name: findField(header, ALIASES.name),
    description: findField(header, ALIASES.description),
  };

  const headerProblems: string[] = [];
  if (columns.date < 0) headerProblems.push('The "date" column is missing.');
  if (columns.amount < 0) headerProblems.push('The "amount" column is missing.');
  if (columns.source < 0) headerProblems.push('The "source" column is missing.');
  if (headerProblems.length > 0) {
    return { rows: [], problems: [], headerProblems };
  }

  const known = new Map(sources.map((source) => [source.name.trim().toLowerCase(), source]));

  const rows: IncomeCsvRow[] = [];
  const problems: IncomeCsvProblem[] = [];

  for (let index = 1; index < lines.length; index++) {
    const line = index + 1;
    const cells = splitLine(lines[index], delimiter);

    const date = (cells[columns.date] ?? '').trim();
    const amount = (cells[columns.amount] ?? '').trim();
    const sourceCell = (cells[columns.source] ?? '').trim();
    const name = columns.name >= 0 ? (cells[columns.name] ?? '').trim() : '';
    const description = columns.description >= 0 ? (cells[columns.description] ?? '').trim() : '';

    if (!ISO_DATE.test(date) || Number.isNaN(new Date(date).getTime())) {
      problems.push({ line, message: `date "${date}" is not YYYY-MM-DD.` });
      continue;
    }
    if (date > today) {
      problems.push({ line, message: `date ${date} is in the future.` });
      continue;
    }
    if (!AMOUNT.test(amount) || Number(amount) <= 0) {
      problems.push({ line, message: `amount "${amount}" must be a number like 4500 or 4500.50.` });
      continue;
    }

    const source = known.get(sourceCell.toLowerCase());
    if (!source) {
      problems.push({ line, message: `source "${sourceCell}" is not one of your income sources.` });
      continue;
    }

    rows.push({
      line,
      date,
      amount: Number(amount),
      sourceName: source.name,
      sourceId: source.id,
      name,
      description,
    });
  }

  return { rows, problems, headerProblems };
}

export const INCOME_CSV_TEMPLATE_HEADER = 'date,amount,source,name,description';

export const INCOME_CSV_TEMPLATE_EXAMPLE = [
  INCOME_CSV_TEMPLATE_HEADER,
  '2026-11-05,480000,Salary,November salary,after tax',
  '2026-11-12,60000,Freelance,Website copy,',
].join('\n');
