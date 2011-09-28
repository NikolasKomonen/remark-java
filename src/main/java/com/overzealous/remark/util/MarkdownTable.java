package com.overzealous.remark.util;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import static com.overzealous.remark.util.MarkdownTable.Alignment.LEFT;
import static com.overzealous.remark.util.MarkdownTable.Alignment.RIGHT;

/**
 * @author Phil DeJarnett
 */
public class MarkdownTable {

	public enum Alignment {
		LEFT(-1), CENTER(0), RIGHT(1);

		private int dir;

		Alignment(int dir) {
			this.dir = dir;
		}

		public int getDir() {
			return dir;
		}

		public static Alignment find(int alignment) {
			if(alignment == 0) {
				return CENTER;
			} else if(alignment < 0) {
				return LEFT;
			} else {
				return RIGHT;
			}
		}
	}

	private List<List<MarkdownTableCell>> header;
	private List<List<MarkdownTableCell>> body;

	private int cols;
	private int[] widths;
	private Alignment[] alignments;


	/**
	 * Creates a new, empty MarkdownTable
	 */
	public MarkdownTable() {
		this.header = new LinkedList<List<MarkdownTableCell>>();
		this.body = new LinkedList<List<MarkdownTableCell>>();
	}

	/**
	 * Creates a new header row, and returns it so it can have cells added to it.
	 *
	 * @return A list that can have columns added to it.
	 */
	public List<MarkdownTableCell> addHeaderRow() {
		List<MarkdownTableCell> newRow = new LinkedList<MarkdownTableCell>();
		this.header.add(newRow);
		return newRow;
	}

	/**
	 * Creates a new body row, and returns it so it can have cells added to it.
	 *
	 * @return A list that can have columns added to it.
	 */
	public List<MarkdownTableCell> addBodyRow() {
		List<MarkdownTableCell> newRow = new LinkedList<MarkdownTableCell>();
		this.body.add(newRow);
		return newRow;
	}

	/**
	 * Renders out the final table.
	 * This process starts by calculating widths and alignment for the columns.  The final output should
	 * be nicely spaced, centered, and look very clean.
	 *
	 * @param output The writer to receive the final output.
	 * @param allowColspan If true, cells that span multiple columns are preserved.  If false, they are rendered in
	 * 						their own column, then empty columns are placed after.
	 * @param renderAsCode If true, the output is rendered as a code block
	 * @throws IOException If an error occurs on the output stream.
	 */
	public void renderTable(Writer output, boolean allowColspan, boolean renderAsCode) throws IOException {
		cols = this.getNumberOfColumns();
		widths = new int[cols];
		alignments = new Alignment[cols];
		for(int i = 0; i<cols; i++) {
			// configure default alignment
			alignments[i] = LEFT;
		}

		this.calculateColumnMetrics(this.header, allowColspan);
		this.calculateColumnMetrics(this.body, allowColspan);

		// now we have our column widths, as well as the alignments
		this.renderRows(output, this.header, allowColspan, renderAsCode);
		this.renderHeaderSeparator(output, renderAsCode);
		this.renderRows(output, this.body, allowColspan, renderAsCode);
	}

	/**
	 * Calculates the column metrics (column widths and alignment) by looping through all provided rows.
	 *
	 * For width, the largest width necessary to render all content is used.
	 *
	 * For alignment, the last non-LEFT alignment in each column is used.  Otherwise, the default LEFT alignment is
	 * used.
	 *
	 * @param rows The rows that should be processed.
	 * @param allowColspan If true, allow cells to span multiple columns.
	 */
	private void calculateColumnMetrics(List<List<MarkdownTableCell>> rows, boolean allowColspan) {
		for(List<MarkdownTableCell> row : rows) {
			int col = 0;
			for(MarkdownTableCell cell : row) {

				if(cell.getAlignment() != LEFT) {
					// if a non-standard alignment, set the column alignment
					// note: the last row gets the alignment preference.
					// since Markdown tables only support one shared alignment,
					// we can't do much about this.
					alignments[col] = cell.getAlignment();
				}

				if( !allowColspan || cell.getColspan() == 1) {

					// single column, just get the maximum width
					widths[col] = Math.max(widths[col], cell.getWidth());

				} else {

					// multiple columns.
					// need to adjust the width based on multiple columns
					int totalWidth = 0;
					for(int i = col; i<col+cell.getColspan(); i++) {
						totalWidth += widths[i];
					}

					// multi-column width is width of content plus two extra spaces for each extra column spanned.
					int cellWidth = cell.getWidth();

					// Only bother if this is wider than other rows combined
					if(cellWidth > totalWidth) {
						System.out.println("---");
						System.out.printf("%d  %d\n", cellWidth, totalWidth);
						// add some to each of the columns
						int diff = cellWidth - totalWidth;
						// this distributes the extra width needed over the columns as evenly as we can
						int addToEveryColumn = diff/cell.getColspan();
						int columnsWithMore = diff % cell.getColspan();
						for(int i = 0; i<cell.getColspan(); i++) {
							int carryOver = 0;
							if(i < columnsWithMore) {
								carryOver = 1;
							}
							widths[i+col] += addToEveryColumn + carryOver;
						}
					}

				}

				// increment our column counter.
				// due to column spanning, we can't rely on the size of the row.
				col += cell.getColspan();
			}
		}
	}

	/**
	 * Render table rows.
	 *
	 * This renders each cell in the row, separated by a '|'
	 *
	 * @param output The writer to send the result to
	 * @param rows The rows to render
	 * @param allowColspan If true, allow cells to span multiple columns
	 * @param renderAsCode If true, prepends each row with four spaces
	 * @throws IOException If an error occurs writing to the output stream
	 */
	private void renderRows(Writer output, List<List<MarkdownTableCell>> rows, boolean allowColspan, boolean renderAsCode) throws IOException {
		for(List<MarkdownTableCell> row : rows) {
			if(renderAsCode) {
				output.write("    ");
			}
			output.write('|');
			int col = 0;
			for(MarkdownTableCell cell : row) {
				Alignment alignment = alignments[col];
				if( !allowColspan || cell.getColspan() == 1) {
					// write the cell
					// we pre-pad the string to ensure that left-and-right alignment look nice
					String contents = String.format(" %s ", cell.getContents());
					StringUtils.align(output, contents, widths[col], alignment.getDir());
					output.write('|');

					if(cell.getColspan() > 1) {
						// clean up colspans when we have them disabled
						for(int emptyCol=col+1; emptyCol < col+cell.getColspan(); emptyCol++) {
							StringUtils.multiply(output, ' ', widths[emptyCol]);
							output.write('|');
						}
					}

				} else {
					// initialize totalWidth to leave room for the extra | chars
					int totalWidth = 0;
					for(int i=col; i < col+cell.getColspan(); i++) {
						totalWidth += widths[i];
					}
					// we pre-pad the string to ensure that left-and-right alignment look nice
					String contents = String.format(" %s ", cell.getContents());
					StringUtils.align(output, contents, totalWidth, alignment.getDir());
					// render out a \ for each column spanned
					StringUtils.multiply(output, '|', cell.getColspan());
				}

				// increment our column counter.
				// due to column spanning, we can't rely on the size of the row.
				col += cell.getColspan();
			}
			output.write('\n');
		}
	}

	private void renderHeaderSeparator(Writer output, boolean renderAsCode) throws IOException {
		// check to see if there's any alignment at all.
		// if not, don't bother rendering the ':'
		boolean alignmentFound = false;
		for(int i=0; i<cols; i++) {
			if(alignments[i] != LEFT) {
				alignmentFound = true;
				break;
			}
		}
		if(renderAsCode) {
			output.write("    ");
		}
		output.write('|');
		for(int i=0; i<cols; i++) {
			int width = widths[i]-2;
			if(alignmentFound) {
				Alignment alignment = alignments[i];
				// for LEFT or CENTER, add an alignment marker
				output.write( alignment == RIGHT ? ' ' : ':');
				// add the header separator
				StringUtils.multiply(output, '-', width);
				// for RIGHT or CENTER, add an alignment marker
				output.write( alignment == LEFT ? ' ' : ':');
			} else {
				// all alignments are left, so don't bother rendering.
				// padding
				output.write(' ');
				// add the header separator
				StringUtils.multiply(output, '-', width);
				// padding
				output.write(' ');
			}
			output.write('|');
		}
		output.write('\n');
	}

	/**
	 * Returns the total number of columns in this table.
	 * This takes into account colspans
	 * @return The total number of columns in this table.
	 */
	public int getNumberOfColumns() {
		int columns = 0;
		for(List<MarkdownTableCell> row : this.header) {
			columns = Math.max(columns, getNumberOfColumnsInRow(row));
		}
		for(List<MarkdownTableCell> row : this.body) {
			columns = Math.max(columns, getNumberOfColumnsInRow(row));
		}
		return columns;
	}

	private int getNumberOfColumnsInRow(List<MarkdownTableCell> row) {
		int count = 0;
		for(MarkdownTableCell cell : row) {
			count += cell.getColspan();
		}
		return count;
	}
}