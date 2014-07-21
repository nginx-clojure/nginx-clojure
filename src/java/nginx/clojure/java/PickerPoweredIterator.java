/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PickerPoweredIterator<E> implements Iterator<E> {

	public static interface Picker<T> {
		public T pick(int i);
		public int size();
	}

	Picker<E> picker;
	int i;

	PickerPoweredIterator(Picker<E> picker) {
		this.picker = picker;
	}

	@Override
	public boolean hasNext() {
		return i < picker.size();
	}

	@Override
	public E next() {
		if (i < picker.size()) {
			return picker.pick(i++);
		}
		throw new NoSuchElementException("NoSuchElementException at " + i);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove not supported now!");
	}

}