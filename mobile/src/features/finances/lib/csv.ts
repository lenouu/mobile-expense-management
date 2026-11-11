import { todayIso } from '@/features/finances/components/finance-ui';

/**
 * Reading a CSV of expenses, before anything is sent anywhere.
 *
 * The rules mirror the ones the expense form and the backend already enforce - a real date, not in
 * the future, a positive amount with at most two decimals, a category the account actually has -
 * so a file that parses here is one the server would accept once the import endpoint exists.
 *
 * Deliberately strict about the header and forgiving about everything else: column order does not
 * matter, extra columns are ignored, `,` and `;` both work (Excel writes `;` in locales where the
 * comma is the decimal separator), and quoted fields may contain the delimiter or a doubled quote.
 */
export type CsvRow = {
  /** The line in the file, so a problem can be pointed at rather than described. */
  line: number;
  date: string;
  amount: string;
  categoryName: string;
  description?: string;
};

export type CsvProblem = {
  line: number;
  message: string;
};

export type CsvParseResult = {
  rows: CsvRow[];
  problems: CsvProblem[];
  /** Non-empty when the file cannot be read at all: no header, or a required column missing. */
  headerProblems: string[];
};

type Field = 'date' | 'amount' | 'category' | 'description';

/** The names a column may be called, lower-cased. The first spelling is what the pop-up shows. */
const ALIASES: Record<Field, string[]> = {
  date: ['date', 'day', 'transaction date', 'spent on'],
  amount: ['amount', 'value', 'total', 'price'],
  category: ['category', 'category name', 'type'],
  description: ['description', 'note', 'notes', 'details', 'label'],
};

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const AMOUNT = /^\d+(\.\d{1,2})?$/;

/** Splits one CSV line, honouring quotes. */
function splitLine(line: string, delimiter: string): string[] {
  const cells: string[] = [];
  let current = '';
  let quoted = false;

  for (let i = 0; i < line.length; i++) {
    const character = line[i];

    if (quoted) {
      if (character === '"') {
        // A doubled quote inside a quoted field is one literal quote.
        if (line[i + 1] === '"') {
          current += '"';
          i++;
        } else {
          quoted = false;
        }
      } else {
        current += character;
      }
      continue;
    }

    if (character === '"') {
      quoted = true;
    } else if (character === delimiter) {
      cells.push(current);
      current = '';
    } else {
      current += character;
    }
  }

  cells.push(current);
  return cells.map((cell) => cell.trim());
}

/** `,` or `;`, whichever the header uses more of. */
function detectDelimiter(headerLine: string): string {
  const commas = (headerLine.match(/,/g) ?? []).length;
  const semicolons = (headerLine.match(/;/g) ?? []).length;
  return semicolons > commas ? ';' : ',';
}

function normaliseHeaderCell(cell: string): string {
  return cell.replace(/^\uFEFF/, '').trim().toLowerCase();
}

function findField(header: string[], aliases: string[]): number {
  return header.findIndex((cell) => aliases.includes(normaliseHeaderCell(cell)));
}

export function parseExpensesCsv(
  text: string,
  categoryNames: string[],
  today: string = todayIso(),
): CsvParseResult {
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
    category: findField(header, ALIASES.category),
    description: findField(header, ALIASES.description),
  };

  const headerProblems: string[] = [];
  if (columns.date < 0) headerProblems.push('The "date" column is missing.');
  if (columns.amount < 0) headerProblems.push('The "amount" column is missing.');
  if (columns.category < 0) headerProblems.push('The "category" column is missing.');
  if (headerProblems.length > 0) {
    return { rows: [], problems: [], headerProblems };
  }

  const known = new Map(categoryNames.map((name) => [name.trim().toLowerCase(), name]));
  const rows: CsvRow[] = [];
  const problems: CsvProblem[] = [];

  for (let index = 1; index < lines.length; index++) {
    const line = index + 1; // the header is line 1
    const cells = splitLine(lines[index], delimiter);

    const date = (cells[columns.date] ?? '').trim();
    const amount = (cells[columns.amount] ?? '').trim();
    const categoryCell = (cells[columns.category] ?? '').trim();
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

    const category = known.get(categoryCell.toLowerCase());
    if (!category) {
      problems.push({ line, message: `category "${categoryCell}" is not one of your categories.` });
      continue;
    }
    if (description.length > 255) {
      problems.push({ line, message: 'description is longer than 255 characters.' });
      continue;
    }

    rows.push({
      line,
      date,
      amount,
      categoryName: category,
      description: description || undefined,
    });
  }

  return { rows, problems, headerProblems: [] };
}

/** The header line the pop-up shows as the template, in the order the columns are described. */
export const CSV_TEMPLATE_HEADER = 'date,amount,category,description';

export const CSV_TEMPLATE_EXAMPLE = [
  CSV_TEMPLATE_HEADER,
  '2026-11-02,4500,Food & Groceries,Lunch with the team',
  '2026-11-02,1200,Transport,Bus tickets',
].join('\n');
