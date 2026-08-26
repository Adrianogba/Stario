/*
 * Copyright (C) 2025 Răzvan Albu
 * Copyright (C) 2026 Adriano Pontes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package adrianogba.stario.launcher.utils.objects

class ObservableObject<A> {
    private val observable: ClosedObservableObject<A>

    constructor(value: A) {
        this.observable = ClosedObservableObject(value)
    }

    constructor(value: A, listener: OnSet<A>) {
        this.observable = ClosedObservableObject(value)
        observable.addListener(listener)
    }

    fun updateObject(value: A) {
        if ((value != null && value == observable.value) || value === observable.value) {
            return
        }

        observable.value = value

        for (listener in observable.listeners) {
            listener?.onSet(value)
        }
    }

    fun getObject(): A = observable.getObject()

    fun addListener(listener: OnSet<A>) {
        observable.addListener(listener)
    }

    fun removeListeners() {
        observable.removeListeners()
    }

    fun getListenerCount(): Int = observable.getListenerCount()

    fun close(): ClosedObservableObject<A> = observable

    class ClosedObservableObject<B>(internal var value: B) {
        internal val listeners = ArrayList<OnSet<B>?>()

        fun addListener(listener: OnSet<B>) {
            listeners.add(listener)
        }

        fun getObject(): B = value

        fun removeListeners() {
            listeners.clear()
        }

        fun getListenerCount(): Int = listeners.size
    }

    fun interface OnSet<G> {
        fun onSet(value: G)
    }
}
